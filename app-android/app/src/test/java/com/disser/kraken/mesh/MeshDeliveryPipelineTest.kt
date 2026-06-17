package com.disser.kraken.mesh

import com.disser.kraken.crypto.AdamovaAdmissionDecision
import com.disser.kraken.crypto.AdamovaBoundCryptoEnvelope
import com.disser.kraken.crypto.AdamovaNativeValidator
import com.disser.kraken.crypto.CryptoProfileKind
import com.disser.kraken.crypto.CryptoProfileRegistry
import com.disser.kraken.crypto.DerivedKey
import com.disser.kraken.crypto.JcaAesGcmAeadProvider
import com.disser.kraken.crypto.KrakenCryptoProfile
import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.crypto.Plaintext
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.message.MessageService
import com.disser.kraken.message.MessageStatus
import com.disser.kraken.nativecore.NativeAdamovaResult
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class MeshDeliveryPipelineTest {
    private val alice = identity("alice", "Alice", "ALICE-FP")
    private val bob = identity("bob", "Bob", "BOB-FP")
    private val alicePeer = DiscoveredPeer("peer-alice", alice.fingerprint, alice.displayName)
    private val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)

    @Test
    fun twoNodeDeliveryAcceptsOnlyActiveQrTrustedRelationships() {
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus) { 1_700_000_000_000 }
        val bobTransport = InMemoryTwoNodeTransport(bobPeer, bus) { 1_700_000_000_000 }
        aliceTransport.start()
        bobTransport.start()
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE)
        val outgoing = MessageService.createOutgoingMessage(
            localIdentity = alice,
            relationship = aliceRelationship,
            body = "hello Bob",
            nowEpochMillis = 1_700_000_000_000,
        )

        val sendResult = MeshOutboxProcessor(
            localIdentity = alice,
            transport = aliceTransport,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_100 },
        ).sendMessage(outgoing, aliceRelationship, bobPeer)

        assertNull(sendResult.rejectedReason)
        assertEquals(MessageStatus.SENT_TO_TRANSPORT, sendResult.updatedMessage?.status)
        val received = bobTransport.observePackets().single()
        val inboxResult = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_200 },
        ).process(received.packet)

        assertNull(inboxResult.rejectedReason)
        assertEquals("hello Bob", inboxResult.acceptedMessage?.body)
        assertNotNull(inboxResult.receiptPacket)

        val receiptSend = bobTransport.send(alicePeer, inboxResult.receiptPacket!!)
        assertTrue(receiptSend.success)
        val receipt = aliceTransport.observePackets().single()
        val receiptResult = MeshInboxProcessor(alice, listOf(aliceRelationship)) { 1_700_000_000_300 }
            .process(receipt.packet)

        assertNull(receiptResult.rejectedReason)
        assertEquals(outgoing.messageId, receiptResult.deliveredMessageId)
        assertEquals(sendResult.packet?.packetId, receiptResult.deliveredPacketId)
    }

    @Test
    fun pendingRelationshipCannotSendOutboundPacket() {
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus) { 1_700_000_000_000 }
        aliceTransport.start()
        val pendingRelationship = relationship(alice, bob, RelationshipState.PENDING_HANDSHAKE)
        val message = outgoingUnsafe(pendingRelationship)

        val result = MeshOutboxProcessor(alice, aliceTransport) { 1_700_000_000_100 }
            .sendMessage(message, pendingRelationship, bobPeer)

        assertEquals(MeshRejectionReason.PENDING_RELATIONSHIP, result.rejectedReason)
    }

    @Test
    fun rejectedCryptoProfileCannotSendOutboundPacket() {
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus) { 1_700_000_000_000 }
        aliceTransport.start()
        val activeRelationship = relationship(alice, bob, RelationshipState.ACTIVE).copy(
            cryptoProfileId = "unknown-experimental-profile",
            admissionDecisionHash = "sha256:unknown",
            profilePolicyVersion = 1,
        )
        val message = outgoingUnsafe(activeRelationship)

        val result = MeshOutboxProcessor(alice, aliceTransport) { 1_700_000_000_100 }
            .sendMessage(message, activeRelationship, bobPeer)

        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_REJECTED, result.rejectedReason)
        assertEquals(MessageStatus.FAILED, result.updatedMessage?.status)
        assertTrue(aliceTransport.observePackets().isEmpty())
    }

    @Test
    fun weakExperimentalProfileBlockedBeforeMessageSendByAdamovaPolicy() {
        val profile = experimentalProfile(profileId = "experimental-weak-two-torsion-v1", a = "-1", b = "0")
        val gate = admissionGateFor(
            profile = profile,
            nativeResult = nativeResult(twoTorsionRootCount = 1, classificationCase = "A5"),
        )
        val admission = gate.evaluate(profile)
        val activeRelationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus) { 1_700_000_000_000 }
        aliceTransport.start()
        val message = outgoingUnsafe(activeRelationship)

        val result = MeshOutboxProcessor(
            localIdentity = alice,
            transport = aliceTransport,
            admissionGate = gate,
            now = { 1_700_000_000_100 },
        ).sendMessage(message, activeRelationship, bobPeer)

        assertEquals(AdamovaAdmissionDecision.REJECT_SMALL_TORSION_RISK, admission.decision)
        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_REJECTED, result.rejectedReason)
        assertEquals(MessageStatus.FAILED, result.updatedMessage?.status)
        assertTrue(aliceTransport.observePackets().isEmpty())
    }

    @Test
    fun sizeGuardedExperimentalProfileBlockedBeforeMessageSendByAdamovaPolicy() {
        val profile = experimentalProfile(profileId = "experimental-size-guarded-v1")
        val gate = admissionGateFor(
            profile = profile,
            nativeResult = nativeResult(classificationCase = "SIZE_GUARDED", earlyStopHit = true),
        )
        val admission = gate.evaluate(profile)
        val activeRelationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus) { 1_700_000_000_000 }
        aliceTransport.start()

        val result = MeshOutboxProcessor(
            localIdentity = alice,
            transport = aliceTransport,
            admissionGate = gate,
            now = { 1_700_000_000_100 },
        ).sendMessage(outgoingUnsafe(activeRelationship), activeRelationship, bobPeer)

        assertEquals(AdamovaAdmissionDecision.SIZE_GUARDED, admission.decision)
        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_REJECTED, result.rejectedReason)
        assertEquals(MessageStatus.FAILED, result.updatedMessage?.status)
        assertTrue(aliceTransport.observePackets().isEmpty())
    }

    @Test
    fun acceptedExperimentalProfileRequiresMatchingPacketMetadata() {
        val profile = experimentalProfile(profileId = "experimental-accepted-a4-v1")
        val gate = admissionGateFor(profile = profile, nativeResult = nativeResult(classificationCase = "A4"))
        val admission = gate.evaluate(profile)
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE).withAdmission(admission)
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus) { 1_700_000_000_000 }
        val bobTransport = InMemoryTwoNodeTransport(bobPeer, bus) { 1_700_000_000_000 }
        aliceTransport.start()
        bobTransport.start()
        val message = outgoingUnsafe(aliceRelationship)

        val sendResult = MeshOutboxProcessor(
            localIdentity = alice,
            transport = aliceTransport,
            admissionGate = gate,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_100 },
        ).sendMessage(message, aliceRelationship, bobPeer)

        assertNull(sendResult.rejectedReason)
        val packet = sendResult.packet
        assertNotNull(packet)
        requireNotNull(packet)
        assertEquals(profile.profileId, packet.cryptoProfileId)
        assertEquals(admission.decisionHash, packet.admissionDecisionHash)
        assertEquals(admission.policyVersion, packet.profilePolicyVersion)
        assertEquals("session-${aliceRelationship.relationshipId}-${profile.profileId}", packet.sessionProfileId)

        val accepted = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            admissionGate = gate,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_200 },
        ).process(packet)

        assertNull(accepted.rejectedReason)
        assertEquals("hello", accepted.acceptedMessage?.body)

        val tamperedSession = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            admissionGate = gate,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_300 },
        ).process(packet.copy(sessionProfileId = "session-tampered"))

        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_MISMATCH, tamperedSession.rejectedReason)
        assertNull(tamperedSession.acceptedMessage)
    }

    @Test
    fun acceptedExperimentalPacketBuildsAdamovaBoundAeadContext() {
        val profile = experimentalProfile(profileId = "experimental-aead-bound-a4-v1")
        val gate = admissionGateFor(profile = profile, nativeResult = nativeResult(classificationCase = "A4"))
        val admission = gate.evaluate(profile)
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val packet = MeshOutboxProcessor(
            localIdentity = alice,
            transport = InMemoryTwoNodeTransport(alicePeer, InMemoryTwoNodeTransport.SharedBus()),
            admissionGate = gate,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_100 },
        ).createMessagePacket(outgoingUnsafe(aliceRelationship), aliceRelationship)
        val context = AdamovaPacketCryptoBinding.contextFor(packet, aliceRelationship, gate).getOrThrow()
        val envelope = AdamovaBoundCryptoEnvelope(gate)
        val aead = JcaAesGcmAeadProvider()
        val key = DerivedKey(ByteArray(32) { 9 })

        val ciphertext = envelope.seal(
            plaintext = Plaintext(packet.payloadJson.encodeToByteArray()),
            key = key,
            context = context,
            aead = aead,
        )
        val opened = envelope.open(ciphertext, key, context, aead)

        assertEquals(packet.payloadJson, opened.bytes.decodeToString())
        val associatedData = envelope.associatedData(context).decodeToString()
        assertTrue(associatedData.contains("adamova_profile_id=${profile.profileId}"))
        assertTrue(associatedData.contains("adamova_admission_decision_hash=${admission.decisionHash}"))
        assertTrue(associatedData.contains("session_profile_id=${packet.sessionProfileId}"))
    }

    @Test
    fun encryptedMessageDeliveryUsesAdamovaBoundPayloadProtector() {
        val profile = experimentalProfile(profileId = "experimental-encrypted-delivery-a4-v1")
        val gate = admissionGateFor(profile = profile, nativeResult = nativeResult(classificationCase = "A4"))
        val admission = gate.evaluate(profile)
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE).withAdmission(admission)
        val sessionKeyProvider = StaticMessageSessionKeyProvider(DerivedKey(ByteArray(32) { 3 }))
        val aliceProtector = AdamovaMessagePayloadProtector(alice, sessionKeyProvider, gate)
        val bobProtector = AdamovaMessagePayloadProtector(bob, sessionKeyProvider, gate)
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus) { 1_700_000_000_000 }
        val bobTransport = InMemoryTwoNodeTransport(bobPeer, bus) { 1_700_000_000_000 }
        aliceTransport.start()
        bobTransport.start()
        val outgoing = MessageService.createOutgoingMessage(alice, aliceRelationship, "encrypted hello")

        val sendResult = MeshOutboxProcessor(
            localIdentity = alice,
            transport = aliceTransport,
            admissionGate = gate,
            messagePayloadProtector = aliceProtector,
            now = { 1_700_000_000_100 },
        ).sendMessage(outgoing, aliceRelationship, bobPeer)

        assertNull(sendResult.rejectedReason)
        val packet = requireNotNull(sendResult.packet)
        assertEquals(PacketPayloadType.ENCRYPTED_MESSAGE_JSON, packet.payloadType)
        assertTrue(!packet.payloadJson.contains("encrypted hello"))

        val received = bobTransport.observePackets().single()
        val inboxResult = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            admissionGate = gate,
            messagePayloadProtector = bobProtector,
            now = { 1_700_000_000_200 },
        ).process(received.packet)

        assertNull(inboxResult.rejectedReason)
        assertEquals("encrypted hello", inboxResult.acceptedMessage?.body)
    }

    @Test
    fun productionProtectionPolicyRejectsOutboundPlaintextWithoutProtector() {
        val activeRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val transport = InMemoryTwoNodeTransport(alicePeer, InMemoryTwoNodeTransport.SharedBus()) {
            1_700_000_000_000
        }
        transport.start()

        val result = MeshOutboxProcessor(
            localIdentity = alice,
            transport = transport,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED,
            now = { 1_700_000_000_100 },
        ).sendMessage(outgoingUnsafe(activeRelationship), activeRelationship, bobPeer)

        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_REJECTED, result.rejectedReason)
        assertEquals(MessageStatus.FAILED, result.updatedMessage?.status)
        assertNull(result.packet)
        assertTrue(transport.observePackets().isEmpty())
    }

    @Test
    fun productionProtectionPolicyRejectsInboundPlaintextMessage() {
        val packet = validMessagePacket()

        val result = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(relationship(bob, alice, RelationshipState.ACTIVE)),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED,
            now = { 1_700_000_000_100 },
        ).process(packet)

        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_REJECTED, result.rejectedReason)
        assertNull(result.acceptedMessage)
    }

    @Test
    fun tamperedPacketSessionCannotBuildAdamovaCryptoContext() {
        val profile = experimentalProfile(profileId = "experimental-aead-tamper-a4-v1")
        val gate = admissionGateFor(profile = profile, nativeResult = nativeResult(classificationCase = "A4"))
        val admission = gate.evaluate(profile)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val packet = MeshOutboxProcessor(
            localIdentity = alice,
            transport = InMemoryTwoNodeTransport(alicePeer, InMemoryTwoNodeTransport.SharedBus()),
            admissionGate = gate,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_100 },
        ).createMessagePacket(outgoingUnsafe(relationship), relationship)

        val result = AdamovaPacketCryptoBinding.contextFor(
            packet.copy(sessionProfileId = "session-tampered"),
            relationship,
            gate,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("CRYPTO_PROFILE_MISMATCH"))
    }

    @Test
    fun encryptedMessageTamperedAdmissionHashIsRejectedBeforePlaintext() {
        val profile = experimentalProfile(profileId = "experimental-encrypted-tamper-a4-v1")
        val gate = admissionGateFor(profile = profile, nativeResult = nativeResult(classificationCase = "A4"))
        val admission = gate.evaluate(profile)
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE).withAdmission(admission)
        val sessionKeyProvider = StaticMessageSessionKeyProvider(DerivedKey(ByteArray(32) { 4 }))
        val protector = AdamovaMessagePayloadProtector(alice, sessionKeyProvider, gate)
        val packet = MeshOutboxProcessor(
            localIdentity = alice,
            transport = InMemoryTwoNodeTransport(alicePeer, InMemoryTwoNodeTransport.SharedBus()),
            admissionGate = gate,
            messagePayloadProtector = protector,
            now = { 1_700_000_000_100 },
        ).createMessagePacket(outgoingUnsafe(aliceRelationship), aliceRelationship)

        val result = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            admissionGate = gate,
            messagePayloadProtector = AdamovaMessagePayloadProtector(bob, sessionKeyProvider, gate),
            now = { 1_700_000_000_200 },
        ).process(packet.copy(admissionDecisionHash = "sha256:tampered"))

        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_MISMATCH, result.rejectedReason)
        assertNull(result.acceptedMessage)
    }

    @Test
    fun receiptPacketPreservesAcceptedExperimentalProfileMetadata() {
        val profile = experimentalProfile(profileId = "experimental-receipt-metadata-v1")
        val gate = admissionGateFor(profile = profile, nativeResult = nativeResult(classificationCase = "A4"))
        val admission = gate.evaluate(profile)
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE).withAdmission(admission)
        val sourceMessage = outgoingUnsafe(aliceRelationship)
        val sourcePacket = MeshOutboxProcessor(
            localIdentity = alice,
            transport = InMemoryTwoNodeTransport(alicePeer, InMemoryTwoNodeTransport.SharedBus()),
            admissionGate = gate,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_100 },
        ).createMessagePacket(sourceMessage, aliceRelationship)

        val accepted = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            admissionGate = gate,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_200 },
        ).process(sourcePacket)

        val receipt = accepted.receiptPacket
        assertNotNull(receipt)
        requireNotNull(receipt)
        assertEquals(sourcePacket.cryptoProfileId, receipt.cryptoProfileId)
        assertEquals(sourcePacket.sessionProfileId, receipt.sessionProfileId)
        assertEquals(sourcePacket.admissionDecisionHash, receipt.admissionDecisionHash)
        assertEquals(sourcePacket.profilePolicyVersion, receipt.profilePolicyVersion)
    }

    @Test
    fun nativeUnavailableFailsClosedForExperimentalProfileBeforeTransport() {
        val profile = experimentalProfile(profileId = "experimental-native-unavailable-v1")
        val gate = admissionGateFor(profile = profile, nativeResult = null)
        val admission = gate.evaluate(profile)
        val activeRelationship = relationship(alice, bob, RelationshipState.ACTIVE).withAdmission(admission)
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus) { 1_700_000_000_000 }
        aliceTransport.start()

        val result = MeshOutboxProcessor(
            localIdentity = alice,
            transport = aliceTransport,
            admissionGate = gate,
            now = { 1_700_000_000_100 },
        ).sendMessage(outgoingUnsafe(activeRelationship), activeRelationship, bobPeer)

        assertEquals(AdamovaAdmissionDecision.NATIVE_UNAVAILABLE, admission.decision)
        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_REJECTED, result.rejectedReason)
        assertTrue(aliceTransport.observePackets().isEmpty())
    }

    @Test
    fun transportFrameFailureIsReportedAsMalformedNotUnknownPeer() {
        val activeRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val message = outgoingUnsafe(activeRelationship)
        val failingTransport = object : PeerTransport {
            override fun start() = Unit
            override fun stop() = Unit
            override fun observePeers(): List<DiscoveredPeer> = listOf(bobPeer)
            override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult =
                TransportSendResult(false, "Kraken packet frame exceeds 262144 bytes.")
            override fun observePackets(): List<ReceivedPacket> = emptyList()
        }

        val result = MeshOutboxProcessor(
            localIdentity = alice,
            transport = failingTransport,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_100 },
        ).sendMessage(message, activeRelationship, bobPeer)

        assertEquals(MeshRejectionReason.MALFORMED, result.rejectedReason)
        assertEquals(MessageStatus.READY_FOR_TRANSPORT, result.updatedMessage?.status)
    }

    @Test
    fun unknownSenderRejectedInbound() {
        val packet = KrakenPacket(
            packetId = "packet-unknown",
            packetType = KrakenPacketType.MESSAGE,
            senderFingerprint = "UNKNOWN-FP",
            recipientFingerprint = bob.fingerprint,
            relationshipId = "relationship-1",
            conversationId = "conversation-relationship-1",
            messageId = "message-1",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 4,
            payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
            payloadJson = """{"message_id":"message-1","body":"hello"}""",
        )

        val result = MeshInboxProcessor(bob, listOf(relationship(bob, alice, RelationshipState.ACTIVE))) { 1_700_000_000_100 }
            .process(packet)

        assertEquals(MeshRejectionReason.UNKNOWN_PEER, result.rejectedReason)
    }

    @Test
    fun wrongRecipientRejectedInbound() {
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val message = MessageService.createOutgoingMessage(alice, aliceRelationship, "hello")
        val packet = MeshOutboxProcessor(
            localIdentity = alice,
            transport = InMemoryTwoNodeTransport(alicePeer, InMemoryTwoNodeTransport.SharedBus()),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 1_700_000_000_100 },
        ).createMessagePacket(message, aliceRelationship).copy(recipientFingerprint = "CAROL-FP")

        val result = MeshInboxProcessor(bob, listOf(relationship(bob, alice, RelationshipState.ACTIVE))) { 1_700_000_000_200 }
            .process(packet)

        assertEquals(MeshRejectionReason.WRONG_RECIPIENT, result.rejectedReason)
    }

    @Test
    fun inboundPacketWithMismatchedCryptoProfileIsRejected() {
        val packet = validMessagePacket().copy(
            cryptoProfileId = "unexpected-experimental-profile",
            admissionDecisionHash = "sha256:unexpected",
        )

        val result = MeshInboxProcessor(bob, listOf(relationship(bob, alice, RelationshipState.ACTIVE))) { 1_700_000_000_100 }
            .process(packet)

        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_MISMATCH, result.rejectedReason)
        assertNull(result.acceptedMessage)
    }

    @Test
    fun duplicateInboundPacketRejected() {
        val packet = KrakenPacket(
            packetId = "packet-duplicate",
            packetType = KrakenPacketType.PING,
            senderFingerprint = alice.fingerprint,
            recipientFingerprint = bob.fingerprint,
            relationshipId = "relationship-1",
            conversationId = "conversation-relationship-1",
            messageId = null,
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 4,
            payloadType = PacketPayloadType.PING_JSON,
            payloadJson = "{}",
        )

        val result = MeshInboxProcessor(bob, listOf(relationship(bob, alice, RelationshipState.ACTIVE))) { 1_700_000_000_100 }
            .process(packet, alreadySeen = true)

        assertEquals(MeshRejectionReason.DUPLICATE, result.rejectedReason)
    }

    @Test
    fun messagePayloadTypeMustMatchPacketType() {
        val packet = validMessagePacket().copy(payloadType = PacketPayloadType.RECEIPT_JSON)

        val result = MeshInboxProcessor(bob, listOf(relationship(bob, alice, RelationshipState.ACTIVE))) { 1_700_000_000_100 }
            .process(packet)

        assertEquals(MeshRejectionReason.MALFORMED, result.rejectedReason)
        assertNull(result.acceptedMessage)
    }

    @Test
    fun messagePayloadIdMustMatchEnvelopeMessageId() {
        val packet = validMessagePacket().copy(
            messageId = "message-envelope",
            payloadJson = """{"message_id":"message-payload","body":"hello"}""",
        )

        val result = MeshInboxProcessor(bob, listOf(relationship(bob, alice, RelationshipState.ACTIVE))) { 1_700_000_000_100 }
            .process(packet)

        assertEquals(MeshRejectionReason.MALFORMED, result.rejectedReason)
        assertNull(result.acceptedMessage)
    }

    @Test
    fun incomingMessageUsesSenderCreatedTimestampWhenPayloadProvidesIt() {
        val packet = validMessagePacket().copy(
            createdAtEpochMillis = 2_000,
            payloadJson = """{"message_id":"message-1","body":"hello","sender_created_at_epoch_millis":1000}""",
        )

        val result = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(relationship(bob, alice, RelationshipState.ACTIVE)),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 9_000 },
        ).process(packet)

        assertNull(result.rejectedReason)
        assertEquals(1_000L, result.acceptedMessage?.createdAtEpochMillis)
    }

    @Test
    fun incomingMessageFallsBackToPacketTimestampForOlderPayloads() {
        val packet = validMessagePacket().copy(
            createdAtEpochMillis = 2_000,
            payloadJson = """{"message_id":"message-1","body":"hello"}""",
        )

        val result = MeshInboxProcessor(
            localIdentity = bob,
            relationships = listOf(relationship(bob, alice, RelationshipState.ACTIVE)),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
            now = { 9_000 },
        ).process(packet)

        assertNull(result.rejectedReason)
        assertEquals(2_000L, result.acceptedMessage?.createdAtEpochMillis)
    }

    @Test
    fun receiptPayloadIdMustMatchEnvelopeMessageId() {
        val receiptPacket = KrakenPacket(
            packetId = "packet-receipt",
            packetType = KrakenPacketType.RECEIPT,
            senderFingerprint = alice.fingerprint,
            recipientFingerprint = bob.fingerprint,
            relationshipId = "relationship-1",
            conversationId = "conversation-relationship-1",
            messageId = "message-envelope",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 4,
            payloadType = PacketPayloadType.RECEIPT_JSON,
            payloadJson = """{"packet_id":"packet-original","message_id":"message-payload"}""",
            sessionProfileId = standardSessionProfileId(),
        )

        val result = MeshInboxProcessor(bob, listOf(relationship(bob, alice, RelationshipState.ACTIVE))) { 1_700_000_000_100 }
            .process(receiptPacket)

        assertEquals(MeshRejectionReason.MALFORMED, result.rejectedReason)
        assertNull(result.deliveredMessageId)
    }

    private fun identity(id: String, name: String, fingerprint: String): LocalIdentity =
        LocalIdentity(
            identityId = id,
            displayName = name,
            publicKeyEncoded = "placeholder-pub:$id",
            privateKeyReference = "placeholder-private-ref:$id",
            fingerprint = fingerprint,
            createdAtEpochMillis = 1_700_000_000_000,
        )

    private fun relationship(
        localIdentity: LocalIdentity,
        peerIdentity: LocalIdentity,
        state: RelationshipState,
    ): Relationship =
        Relationship(
            relationshipId = "relationship-1",
            localIdentityPublicKey = localIdentity.publicKeyEncoded,
            peerPublicKey = peerIdentity.publicKeyEncoded,
            peerDisplayName = peerIdentity.displayName,
            peerFingerprint = peerIdentity.fingerprint,
            realmId = null,
            state = state,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
            sourceInviteId = "invite-1",
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
        )

    private fun validMessagePacket(): KrakenPacket =
        KrakenPacket(
            packetId = "packet-message",
            packetType = KrakenPacketType.MESSAGE,
            senderFingerprint = alice.fingerprint,
            recipientFingerprint = bob.fingerprint,
            relationshipId = "relationship-1",
            conversationId = "conversation-relationship-1",
            messageId = "message-1",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 4,
            payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
            payloadJson = """{"message_id":"message-1","body":"hello"}""",
            sessionProfileId = standardSessionProfileId(),
        )

    private fun outgoingUnsafe(relationship: Relationship) =
        com.disser.kraken.message.LocalMessage(
            messageId = "message-unsafe",
            conversationId = MessageService.conversationIdFor(relationship),
            relationshipId = relationship.relationshipId,
            peerFingerprint = relationship.peerFingerprint,
            direction = com.disser.kraken.message.MessageDirection.OUTGOING,
            status = MessageStatus.READY_FOR_TRANSPORT,
            body = "hello",
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
        )

    private fun standardSessionProfileId(): String =
        "session-relationship-1-${KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID}"

    private fun Relationship.withAdmission(admission: com.disser.kraken.crypto.ProductCryptoAdmissionResult): Relationship =
        copy(
            cryptoProfileId = admission.profileId,
            cryptoProfileHash = admission.profileHash,
            admissionDecisionHash = admission.decisionHash,
            profilePolicyVersion = admission.policyVersion,
            nativeBackendVersion = admission.nativeBackendVersion,
        )

    private fun experimentalProfile(
        profileId: String,
        a: String = "65537",
        b: String = "104729",
    ): KrakenCryptoProfile =
        KrakenCryptoProfile(
            profileId = profileId,
            profileVersion = 1,
            profileKind = CryptoProfileKind.EXPERIMENTAL_ADAMOVA_CURVE_PROFILE,
            curveA = a,
            curveB = b,
        )

    private fun admissionGateFor(
        profile: KrakenCryptoProfile,
        nativeResult: NativeAdamovaResult?,
    ): ProductCryptoAdmissionGate =
        ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(nativeResult),
            registry = SingleProfileRegistry(profile),
            now = { 1_700_000_000_000 },
        )

    private class SingleProfileRegistry(private val profile: KrakenCryptoProfile) : CryptoProfileRegistry {
        override fun find(profileId: String): KrakenCryptoProfile? =
            when (profileId) {
                profile.profileId -> profile
                KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID -> com.disser.kraken.crypto.DefaultCryptoProfileRegistry.standardProfile
                else -> null
            }
    }

    private class FakeAdamovaValidator(private val result: NativeAdamovaResult?) : AdamovaNativeValidator {
        override fun status(): String = "fake-native-adamova-v1"
        override fun classify(a: BigInteger, b: BigInteger): NativeAdamovaResult? = result
    }

    private class StaticMessageSessionKeyProvider(private val key: DerivedKey) : MessageSessionKeyProvider {
        override fun keyFor(
            localIdentity: LocalIdentity,
            relationship: Relationship,
            packet: KrakenPacket,
        ): DerivedKey = key
    }

    private fun nativeResult(
        singular: Boolean = false,
        twoTorsionRootCount: Int = 0,
        threeTorsionRootCount: Int = 0,
        hasThreeTorsionIndicator: Boolean = false,
        classificationCase: String = "A4",
        earlyStopHit: Boolean = false,
    ): NativeAdamovaResult =
        NativeAdamovaResult(
            a = BigInteger.valueOf(65537),
            b = BigInteger.valueOf(104729),
            singular = singular,
            discriminant = BigInteger.ONE,
            twoTorsionRootCount = twoTorsionRootCount,
            twoTorsionRoots = emptyList(),
            threeTorsionRootCount = threeTorsionRootCount,
            threeTorsionRoots = emptyList(),
            hasThreeTorsionIndicator = hasThreeTorsionIndicator,
            hasThreeTorsionInconsistency = false,
            classificationCase = classificationCase,
            roots3CandidatesTotal = 0,
            roots3RejectedMod = 0,
            roots3RejectedBound = 0,
            roots3PassedFilters = 0,
            roots3ExactChecked = 0,
            roots3ExactZero = 0,
            roots3SquarecheckPass = 0,
            divisorCountA2 = 0,
            factorizationSteps = 0,
            xSquare = emptyList(),
            earlyStopHit = earlyStopHit,
        )
}
