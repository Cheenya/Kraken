package com.disser.kraken.mesh

import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.handshake.HandshakePayloadCodec
import com.disser.kraken.handshake.OfflineHandshakeService
import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.IssuedInviteRecord
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageService
import com.disser.kraken.message.MessageStatus
import com.disser.kraken.realm.CapacityState
import com.disser.kraken.realm.LocalRealmState
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmPolicy
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshServiceTest {
    @Test
    fun meshStartsOnlyWithIdentityAndReportsPrototypeWarning() {
        val service = MeshService()

        val error = service.start(null)
        assertEquals(MeshState.ERROR, error.state)
        assertEquals("identity-required", error.lastPacketStatus)

        val started = service.start(identity())
        assertEquals(MeshState.SCANNING, started.state)
        assertEquals("loopback-prototype", started.transportMode)
        assertEquals("loopback-ready", started.lastPacketStatus)
        assertTrue(started.prototypeWarning.contains("сообщения идут через защищённый payload path"))
    }

    @Test
    fun queuedReadyMessagesSurviveServiceRestartSnapshot() {
        val service = MeshService()
        val messages = listOf(message(MessageStatus.READY_FOR_TRANSPORT), message(MessageStatus.DELIVERED_TO_PEER))

        service.start(identity())
        val stopped = service.stop(messages)

        assertEquals(MeshState.OFF, stopped.state)
        assertEquals(1, stopped.queuedPackets)
        assertEquals(1, stopped.metrics.packetsQueued)
    }

    @Test
    fun metricsRecorderTracksCoreCounters() {
        val service = MeshService()
        val messages = listOf(message(MessageStatus.READY_FOR_TRANSPORT))

        service.recordSend(messages)
        service.recordReceive(messages)
        service.recordReceipt(messages, latencyMs = 42)
        val snapshot = service.recordDrop(messages, MeshRejectionReason.UNKNOWN_PEER)

        assertEquals(1, snapshot.metrics.packetsQueued)
        assertEquals(1, snapshot.metrics.packetsSent)
        assertEquals(1, snapshot.metrics.packetsReceived)
        assertEquals(1, snapshot.metrics.receiptsReceived)
        assertEquals(1, snapshot.metrics.unknownPeerRejected)
        assertEquals(42L, snapshot.metrics.lastDeliveryLatencyMs)
    }

    @Test
    fun snapshotExposesPeerRouteEvidenceFromTransportDiagnostics() {
        val bobPeer = DiscoveredPeer("peer-bob", "BOB-FP", "Bob")
        val evidence = DiscoveredPeerRouteEvidence(
            fingerprint = bobPeer.fingerprint,
            transportId = KrakenTransportCatalog.BLE_GATT.id,
            observedAtEpochMillis = 10_000,
        )
        val transport = FakePeerTransport(
            localFingerprint = "ALICE-FP",
            peers = listOf(bobPeer),
            diagnostics = MeshTransportDiagnostics(
                discoveredPeerCount = 1,
                peerRouteEvidence = listOf(evidence),
            ),
        )
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val snapshot = service.snapshot(emptyList())

        assertEquals(listOf(evidence), snapshot.peerRouteEvidence)
        assertEquals(listOf(evidence), snapshot.transportDiagnostics.peerRouteEvidence)
    }

    @Test
    fun snapshotExposesRealmRelayCandidateWithoutDirectSendPeerPromotion() {
        val alice = identity("alice", "Alice", FingerprintFormatter.shortFingerprint("placeholder-pub:alice"))
        val relay = identity("relay", "Relay", FingerprintFormatter.shortFingerprint("placeholder-pub:relay"))
        val relayPeer = DiscoveredPeer("peer-relay", relay.fingerprint, relay.displayName)
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(relayPeer),
        )
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val snapshot = service.snapshot(
            messages = emptyList(),
            localIdentity = alice,
            realmSnapshot = realmSnapshot(
                certificates = listOf(
                    certificate(alice, listOf("send_direct", "relay_basic")),
                    certificate(relay, listOf("relay_basic")),
                ),
            ),
        )

        assertEquals(listOf(relayPeer), snapshot.discoveredPeers)
        assertEquals(1, snapshot.realmRelayCandidates.size)
        assertEquals(relay.fingerprint.fingerprintPrefix(), snapshot.realmRelayCandidates.single().peerFingerprintPrefix)
        assertEquals(REALM_ID, snapshot.realmRelayCandidates.single().realmId)
    }

    @Test
    fun debugDirectSendAllowsTransportFallbackWhenPeerNotObservedYet() {
        val alice = identity()
        val bob = identity(id = "bob", name = "Bob", fingerprint = "BOB-FP")
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val message = MessageService.createOutgoingMessage(alice, relationship, "hello")
        val transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList())
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val result = service.sendDebugDirectMessage(
            messages = emptyList(),
            localIdentity = alice,
            relationship = relationship,
            message = message,
            realmSnapshot = RealmSnapshot(
                realms = emptyList(),
                membershipCertificates = emptyList(),
                inviteEdges = emptyList(),
                pendingRequests = emptyList(),
            ),
        )

        assertTrue(result.success)
        assertEquals(listOf(bob.fingerprint), transport.sentPeers.map { it.fingerprint })
        assertEquals(1, transport.sentPackets.size)
    }

    @Test
    fun syncNowReturnsTransportDisabledWithoutIdentityOrTransport() {
        val service = MeshService()

        val result = service.syncNow(
            localIdentity = identity(),
            relationships = emptyList(),
            messages = listOf(message(MessageStatus.READY_FOR_TRANSPORT)),
        )

        assertEquals(0, result.sentCount)
        assertEquals(MeshState.OFF, result.snapshot.state)
        assertEquals("transport-disabled", result.snapshot.lastPacketStatus)
        assertEquals("sent=0 received=0 receipts=0 rejected=0", result.snapshot.lastSyncSummary)
    }

    @Test
    fun snapshotIncludesTransportDiagnosticsForLanSmokeDebugging() {
        val bobPeer = DiscoveredPeer("peer-bob", "BOB-FP", "Bob")
        val transport = FakePeerTransport(
            localFingerprint = "ALICE-FP",
            peers = listOf(bobPeer),
            diagnostics = MeshTransportDiagnostics(
                localPort = 49152,
                localAddresses = listOf("192.168.1.10"),
                registrationState = "registered:Kraken-ALICE",
                discoveryState = "started:_kraken._tcp.",
                discoveredPeerCount = 1,
                manualPeerCount = 0,
                sendFailures = 1,
                lastError = "send:peer-not-found",
            ),
        )
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val snapshot = service.snapshot(emptyList())

        assertEquals(1, snapshot.discoveredPeers.size)
        assertEquals(49152, snapshot.transportDiagnostics.localPort)
        assertEquals(listOf("192.168.1.10"), snapshot.transportDiagnostics.localAddresses)
        assertEquals("registered:Kraken-ALICE", snapshot.transportDiagnostics.registrationState)
        assertEquals("started:_kraken._tcp.", snapshot.transportDiagnostics.discoveryState)
        assertEquals("send:peer-not-found", snapshot.transportDiagnostics.lastError)
    }

    @Test
    fun manualLanPeerFallbackAddsTransportPeerWithoutChangingTrust() {
        val transport = FakeManualLanTransport(localFingerprint = "ALICE-FP")
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val snapshot = service.addManualLanPeer(
            messages = emptyList(),
            fingerprint = "BOB-FP",
            host = "192.168.1.22",
            port = 44881,
            displayName = "Bob",
        )

        assertEquals("manual-peer-added", snapshot.lastPacketStatus)
        assertEquals(1, snapshot.discoveredPeers.size)
        assertEquals("BOB-FP", snapshot.discoveredPeers.single().fingerprint)
        assertEquals(1, snapshot.transportDiagnostics.manualPeerCount)
    }

    @Test
    fun manualLanPeerFallbackIsUnavailableForNonLanTransport() {
        val service = MeshService(
            transportManager = TransportManager().apply {
                setForTest(FakePeerTransport(localFingerprint = "ALICE-FP", peers = emptyList()))
            },
        )

        val snapshot = service.addManualLanPeer(
            messages = emptyList(),
            fingerprint = "BOB-FP",
            host = "192.168.1.22",
            port = 44881,
        )

        assertEquals("manual-peer-unavailable", snapshot.lastPacketStatus)
        assertEquals(0, snapshot.discoveredPeers.size)
    }

    @Test
    fun manualLanPeerFallbackRejectsSelfFingerprint() {
        val transport = FakeManualLanTransport(localFingerprint = "ALICE-FP")
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val snapshot = service.addManualLanPeer(
            messages = emptyList(),
            fingerprint = "ALICE-FP",
            host = "192.168.1.22",
            port = 44881,
        )

        assertEquals("manual-peer-failed:self-fingerprint", snapshot.lastPacketStatus)
        assertEquals(0, snapshot.discoveredPeers.size)
    }


    @Test
    fun syncNowSendsReadyMessagesToDiscoveredActivePeer() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = listOf(bobPeer))
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )

        assertEquals(1, result.sentCount)
        assertEquals(MessageStatus.SENT_TO_TRANSPORT, result.messages.single().status)
        assertEquals(1, transport.sentPackets.size)
        assertEquals(PacketPayloadType.ENCRYPTED_MESSAGE_JSON, transport.sentPackets.single().payloadType)
        assertTrue(!transport.sentPackets.single().payloadJson.contains("hello"))
        assertEquals("messages-sent-1", result.snapshot.lastPacketStatus)
        assertEquals("sent=1 received=0 receipts=0 rejected=0", result.snapshot.lastSyncSummary)
        assertTrue(result.snapshot.lastSyncAtEpochMillis != null)
    }

    @Test
    fun syncNowRejectsInboundPlaintextMessageByDefault() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val alicePeer = DiscoveredPeer("peer-alice", alice.fingerprint, alice.displayName)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE)
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val plaintextPacket = MeshOutboxProcessor(
            localIdentity = alice,
            transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList()),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        ).createMessagePacket(
            message(
                status = MessageStatus.READY_FOR_TRANSPORT,
                relationshipId = aliceRelationship.relationshipId,
                peerFingerprint = bob.fingerprint,
            ),
            aliceRelationship,
        )
        val transport = FakePeerTransport(
            localFingerprint = bob.fingerprint,
            peers = listOf(alicePeer),
            inbound = listOf(ReceivedPacket(alicePeer, plaintextPacket, 1_700_000_000_100)),
        )
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val result = service.syncNow(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            messages = emptyList(),
        )

        assertEquals(0, result.receivedCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_REJECTED, result.snapshot.recentRejectedInboundPackets.single().reason)
        assertTrue(result.messages.isEmpty())
    }

    @Test
    fun syncNowRejectsQueuedRealmMessageWhenPeerMembershipRemoved() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = listOf(bobPeer))
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE, realmId = REALM_ID)
        val outgoing = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
            realmSnapshot = realmSnapshot(certificates = listOf(certificate(alice, listOf("send_direct")))),
        )

        assertEquals(0, result.sentCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(0, transport.sentPackets.size)
        assertEquals("rejected-1", result.snapshot.lastPacketStatus)
    }

    @Test
    fun syncNowAppliesReceiptToOutgoingMessage() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.SENT_TO_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val receiptPacket = KrakenPacket(
            packetId = "packet-receipt",
            packetType = KrakenPacketType.RECEIPT,
            senderFingerprint = bob.fingerprint,
            recipientFingerprint = alice.fingerprint,
            relationshipId = relationship.relationshipId,
            conversationId = outgoing.conversationId,
            messageId = outgoing.messageId,
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = Long.MAX_VALUE,
            ttlHops = 4,
            payloadType = PacketPayloadType.RECEIPT_JSON,
            payloadJson = """{"packet_id":"packet-message","message_id":"${outgoing.messageId}"}""",
            sessionProfileId = standardSessionProfileId(relationship),
        )
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            inbound = listOf(ReceivedPacket(bobPeer, receiptPacket, 1_700_000_000_100)),
        )
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )

        assertEquals(1, result.receiptsApplied)
        assertEquals(MessageStatus.DELIVERED_TO_PEER, result.messages.single().status)
        assertEquals("sent=0 received=0 receipts=1 rejected=0", result.snapshot.lastSyncSummary)
    }

    @Test
    fun inboundMessageQueuesReceiptWhenImmediateSendFailsAndRetriesLater() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val alicePeer = DiscoveredPeer("peer-alice", alice.fingerprint, alice.displayName)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE)
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val sourceMessage = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = aliceRelationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val sourcePacket = MeshOutboxProcessor(
            localIdentity = alice,
            transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList()),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        ).createMessagePacket(sourceMessage, aliceRelationship)
        val repository = inMemoryRepository()
        val transport = FakePeerTransport(
            localFingerprint = bob.fingerprint,
            peers = listOf(alicePeer),
            inbound = listOf(ReceivedPacket(alicePeer, sourcePacket, 1_700_000_000_100)),
            failSends = true,
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        )

        val received = service.syncNow(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            messages = emptyList(),
        )

        assertEquals(1, received.receivedCount)
        assertEquals(1, received.rejectedCount)
        val queuedReceipt = repository.outboxStore.queuedReceipts().single()
        assertEquals(KrakenPacketType.RECEIPT, queuedReceipt.packet.packetType)
        assertEquals(PacketStoreStatus.QUEUED, queuedReceipt.status)
        assertEquals(MeshRejectionReason.UNKNOWN_PEER, queuedReceipt.lastError)

        transport.failSends = false
        val retried = service.syncNow(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            messages = received.messages,
        )

        assertEquals(1, retried.sentCount)
        assertEquals(PacketStoreStatus.SENT, repository.outboxStore.load().single().status)
        assertEquals(2, transport.sentPackets.size)
    }

    @Test
    fun queuedReceiptRetryRejectsTamperedCryptoProfileMetadataBeforeTransport() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val alicePeer = DiscoveredPeer("peer-alice", alice.fingerprint, alice.displayName)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE)
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val sourceMessage = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = aliceRelationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val sourcePacket = MeshOutboxProcessor(
            localIdentity = alice,
            transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList()),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        ).createMessagePacket(sourceMessage, aliceRelationship)
        val repository = inMemoryRepository()
        val transport = FakePeerTransport(
            localFingerprint = bob.fingerprint,
            peers = listOf(alicePeer),
            inbound = listOf(ReceivedPacket(alicePeer, sourcePacket, 1_700_000_000_100)),
            failSends = true,
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        )
        val received = service.syncNow(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            messages = emptyList(),
        )
        val queuedReceipt = repository.outboxStore.queuedReceipts().single()
        repository.outboxStore.upsert(
            queuedReceipt.copy(
                packet = queuedReceipt.packet.copy(admissionDecisionHash = "sha256:tampered"),
            ),
        )

        transport.failSends = false
        val retried = service.syncNow(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            messages = received.messages,
        )

        assertEquals(0, retried.sentCount)
        assertEquals(1, retried.rejectedCount)
        assertEquals(1, transport.sentPackets.size)
        val rejected = repository.outboxStore.load().single()
        assertEquals(PacketStoreStatus.REJECTED, rejected.status)
        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_MISMATCH, rejected.lastError)
    }

    @Test
    fun inboundReceiptMarksMatchingOutboxPacketAcked() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.SENT_TO_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val originalPacket = MeshOutboxProcessor(
            localIdentity = alice,
            transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList()),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        ).createMessagePacket(outgoing, relationship).copy(packetId = "packet-message")
        val receiptPacket = receiptPacket(
            sender = bob,
            recipient = alice,
            relationship = relationship,
            originalPacket = originalPacket,
        )
        val repository = inMemoryRepository()
        repository.outboxStore.upsert(
            StoredPacket(
                packet = originalPacket,
                status = PacketStoreStatus.SENT,
                storedAtEpochMillis = 1_700_000_000_000,
                lastAttemptAtEpochMillis = 1_700_000_000_000,
            ),
        )
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            inbound = listOf(ReceivedPacket(bobPeer, receiptPacket, 1_700_000_000_100)),
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )

        assertEquals(1, result.receiptsApplied)
        assertEquals(MessageStatus.DELIVERED_TO_PEER, result.messages.single().status)
        assertEquals(PacketStoreStatus.ACKED, repository.outboxStore.load().single().status)
        assertEquals(100L, result.snapshot.metrics.lastDeliveryLatencyMs)
    }

    @Test
    fun inboundReceiptMarksAllStoredPacketsForMessageAcked() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.SENT_TO_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val originalPacket = MeshOutboxProcessor(
            localIdentity = alice,
            transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList()),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        ).createMessagePacket(outgoing, relationship).copy(packetId = "packet-message")
        val duplicateStoredPacket = originalPacket.copy(packetId = "packet-message-retry")
        val receiptPacket = receiptPacket(
            sender = bob,
            recipient = alice,
            relationship = relationship,
            originalPacket = originalPacket,
        )
        val repository = inMemoryRepository()
        repository.outboxStore.upsert(
            StoredPacket(
                packet = originalPacket,
                status = PacketStoreStatus.SENT,
                storedAtEpochMillis = 1_700_000_000_000,
                lastAttemptAtEpochMillis = 1_700_000_000_000,
            ),
        )
        repository.outboxStore.upsert(
            StoredPacket(
                packet = duplicateStoredPacket,
                status = PacketStoreStatus.QUEUED,
                storedAtEpochMillis = 1_700_000_000_001,
                nextAttemptAtEpochMillis = 0,
            ),
        )
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            inbound = listOf(ReceivedPacket(bobPeer, receiptPacket, 1_700_000_000_100)),
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )

        assertEquals(1, result.receiptsApplied)
        assertEquals(MessageStatus.DELIVERED_TO_PEER, result.messages.single().status)
        assertEquals(listOf(PacketStoreStatus.ACKED, PacketStoreStatus.ACKED), repository.outboxStore.load().map { it.status })
    }

    @Test
    fun deliveredMessageWithStaleQueuedPacketDoesNotRetryOrDowngrade() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val delivered = message(
            status = MessageStatus.DELIVERED_TO_PEER,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val packet = MeshOutboxProcessor(
            localIdentity = alice,
            transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList()),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        ).createMessagePacket(delivered, relationship).copy(packetId = "packet-stale")
        val repository = inMemoryRepository()
        repository.outboxStore.upsert(
            StoredPacket(
                packet = packet,
                status = PacketStoreStatus.QUEUED,
                storedAtEpochMillis = 1_700_000_000_000,
                nextAttemptAtEpochMillis = 0,
            ),
        )
        val transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = listOf(bobPeer))
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(delivered),
        )

        assertEquals(0, result.sentCount)
        assertEquals(0, transport.sentPackets.size)
        assertEquals(MessageStatus.DELIVERED_TO_PEER, result.messages.single().status)
        assertEquals(PacketStoreStatus.ACKED, repository.outboxStore.load().single().status)
    }

    @Test
    fun duplicateReceiptForAckedOutboxPacketIsNoOp() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val delivered = message(
            status = MessageStatus.DELIVERED_TO_PEER,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val originalPacket = MeshOutboxProcessor(
            localIdentity = alice,
            transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList()),
            messagePayloadProtectionPolicy = MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED,
        ).createMessagePacket(delivered, relationship).copy(packetId = "packet-message")
        val receiptPacket = receiptPacket(
            sender = bob,
            recipient = alice,
            relationship = relationship,
            originalPacket = originalPacket,
        ).copy(packetId = "packet-duplicate-receipt")
        val repository = inMemoryRepository()
        repository.outboxStore.upsert(StoredPacket(originalPacket, PacketStoreStatus.ACKED, 1))
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            inbound = listOf(ReceivedPacket(bobPeer, receiptPacket, 1_700_000_000_100)),
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(delivered),
        )

        assertEquals(0, result.receiptsApplied)
        assertEquals(MessageStatus.DELIVERED_TO_PEER, result.messages.single().status)
        assertEquals(PacketStoreStatus.ACKED, repository.outboxStore.load().single().status)
    }

    @Test
    fun failedOutgoingMessageStaysQueuedAndDoesNotDuplicateBeforeBackoff() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val repository = inMemoryRepository()
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            failSends = true,
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )

        val failed = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )

        assertEquals(0, failed.sentCount)
        assertEquals(1, failed.rejectedCount)
        assertEquals(1, transport.sentPackets.size)
        val queued = repository.outboxStore.load().single()
        assertEquals(PacketStoreStatus.QUEUED, queued.status)
        assertEquals(1, queued.attempts)
        assertEquals(MeshRejectionReason.UNKNOWN_PEER, queued.lastError)
        assertTrue((queued.lastAttemptAtEpochMillis ?: 0) < queued.nextAttemptAtEpochMillis)

        transport.failSends = false
        val beforeBackoff = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = failed.messages,
        )

        assertEquals(0, beforeBackoff.sentCount)
        assertEquals(1, transport.sentPackets.size)
        assertEquals(PacketStoreStatus.QUEUED, repository.outboxStore.load().single().status)
    }

    @Test
    fun readyMessageCreatesPersistentQueuedPacketWhenTrustedPeerNotDiscovered() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val repository = inMemoryRepository()
        val transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = emptyList())
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )

        val first = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )

        assertEquals(0, first.sentCount)
        assertEquals(1, first.rejectedCount)
        assertEquals(0, transport.sentPackets.size)
        val queued = repository.outboxStore.load().single()
        assertEquals(PacketStoreStatus.QUEUED, queued.status)
        assertEquals(KrakenPacketType.MESSAGE, queued.packet.packetType)
        assertEquals(outgoing.messageId, queued.packet.messageId)
        assertEquals(MeshRejectionReason.UNKNOWN_PEER, queued.lastError)
        assertEquals(1, first.snapshot.queue.outboxQueued)
        assertEquals(1, first.snapshot.queuedPackets)

        val beforeBackoff = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = first.messages,
        )

        assertEquals(0, beforeBackoff.sentCount)
        assertEquals(0, transport.sentPackets.size)
        assertEquals(1, repository.outboxStore.load().size)
    }

    @Test
    fun snapshotUsesStoredOutboxQueueWithoutDoubleCountingReadyMessage() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val repository = inMemoryRepository()
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            failSends = true,
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )

        val failed = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )

        assertEquals(1, failed.snapshot.queuedPackets)
        assertEquals(1, failed.snapshot.queue.pendingForDelivery)
        assertEquals(1, failed.snapshot.queue.readyMessages)
        assertEquals(1, failed.snapshot.queue.outboxQueued)
        assertEquals(0, failed.snapshot.queue.sentAwaitingAck)
        assertEquals(1, failed.snapshot.queue.storedOutboxPackets)
        assertEquals(MeshRejectionReason.UNKNOWN_PEER, failed.snapshot.queue.lastError)
        assertTrue(failed.snapshot.queue.nextAttemptAtEpochMillis != null)
    }

    @Test
    fun malformedTransportFailureRejectsStoredPacketInsteadOfRetryingForever() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val repository = inMemoryRepository()
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            failSends = true,
            failureError = "Kraken packet frame exceeds 262144 bytes.",
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )

        assertEquals(0, result.sentCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(MessageStatus.FAILED, result.messages.single().status)
        val rejected = repository.outboxStore.load().single()
        assertEquals(PacketStoreStatus.REJECTED, rejected.status)
        assertEquals(MeshRejectionReason.MALFORMED, rejected.lastError)
        assertEquals(0, rejected.nextAttemptAtEpochMillis)
    }

    @Test
    fun queuedOutgoingMessageRetriesSamePacketAfterBackoff() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val repository = inMemoryRepository()
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            failSends = true,
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )
        val failed = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )
        val queued = repository.outboxStore.load().single()
        repository.outboxStore.upsert(queued.copy(nextAttemptAtEpochMillis = 0))

        transport.failSends = false
        val retried = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = failed.messages,
        )

        assertEquals(1, retried.sentCount)
        assertEquals(2, transport.sentPackets.size)
        assertEquals(transport.sentPackets.first().packetId, transport.sentPackets.last().packetId)
        assertEquals(MessageStatus.SENT_TO_TRANSPORT, retried.messages.single().status)
        val sent = repository.outboxStore.load().single()
        assertEquals(PacketStoreStatus.SENT, sent.status)
        assertEquals(2, sent.attempts)
        assertEquals(queued.packet.cryptoProfileId, transport.sentPackets.last().cryptoProfileId)
        assertEquals(queued.packet.sessionProfileId, transport.sentPackets.last().sessionProfileId)
        assertEquals(queued.packet.admissionDecisionHash, transport.sentPackets.last().admissionDecisionHash)
        assertEquals(queued.packet.profilePolicyVersion, transport.sentPackets.last().profilePolicyVersion)
    }

    @Test
    fun queuedOutgoingMessageRejectsTamperedCryptoProfileMetadataBeforeRetryTransport() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val outgoing = message(
            status = MessageStatus.READY_FOR_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        )
        val repository = inMemoryRepository()
        val transport = FakePeerTransport(
            localFingerprint = alice.fingerprint,
            peers = listOf(bobPeer),
            failSends = true,
        )
        val service = MeshService(
            transportManager = TransportManager().apply { setForTest(transport) },
            repository = repository,
        )
        val failed = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(outgoing),
        )
        val queued = repository.outboxStore.load().single()
        repository.outboxStore.upsert(
            queued.copy(
                packet = queued.packet.copy(sessionProfileId = "session-tampered"),
                nextAttemptAtEpochMillis = 0,
            ),
        )

        transport.failSends = false
        val retried = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = failed.messages,
        )

        assertEquals(0, retried.sentCount)
        assertEquals(1, retried.rejectedCount)
        assertEquals(1, transport.sentPackets.size)
        assertEquals(MessageStatus.FAILED, retried.messages.single().status)
        val rejected = repository.outboxStore.load().single()
        assertEquals(PacketStoreStatus.REJECTED, rejected.status)
        assertEquals(MeshRejectionReason.CRYPTO_PROFILE_MISMATCH, rejected.lastError)
    }

    @Test
    fun syncNowRetriesStaleSentMessageWhenReceiptDidNotArrive() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val staleSent = message(
            status = MessageStatus.SENT_TO_TRANSPORT,
            relationshipId = relationship.relationshipId,
            peerFingerprint = bob.fingerprint,
        ).copy(updatedAtEpochMillis = 1)
        val transport = FakePeerTransport(localFingerprint = alice.fingerprint, peers = listOf(bobPeer))
        val service = MeshService(transportManager = TransportManager().apply { setForTest(transport) })

        val result = service.syncNow(
            localIdentity = alice,
            relationships = listOf(relationship),
            messages = listOf(staleSent),
        )

        assertEquals(1, result.sentCount)
        assertEquals(MessageStatus.SENT_TO_TRANSPORT, result.messages.single().status)
        assertEquals(1, transport.sentPackets.size)
    }

    @Test
    fun twoNodeServicesDeliverMessageAndReceiptThroughSharedTransport() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val alicePeer = DiscoveredPeer("peer-alice", alice.fingerprint, alice.displayName)
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus)
        val bobTransport = InMemoryTwoNodeTransport(bobPeer, bus)
        aliceTransport.start()
        bobTransport.start()
        val aliceService = MeshService(transportManager = TransportManager().apply { setForTest(aliceTransport) })
        val bobService = MeshService(transportManager = TransportManager().apply { setForTest(bobTransport) })
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val bobRelationship = relationship(bob, alice, RelationshipState.ACTIVE)
        val outgoing = com.disser.kraken.message.MessageService.createOutgoingMessage(
            localIdentity = alice,
            relationship = aliceRelationship,
            body = "hello over mesh",
            nowEpochMillis = 1_700_000_000_000,
        )

        val aliceSent = aliceService.syncNow(
            localIdentity = alice,
            relationships = listOf(aliceRelationship),
            messages = listOf(outgoing),
        )
        val bobReceived = bobService.syncNow(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            messages = emptyList(),
        )
        val aliceDelivered = aliceService.syncNow(
            localIdentity = alice,
            relationships = listOf(aliceRelationship),
            messages = aliceSent.messages,
        )

        assertEquals(1, aliceSent.sentCount)
        assertEquals(MessageStatus.SENT_TO_TRANSPORT, aliceSent.messages.single().status)
        assertEquals(1, bobReceived.receivedCount)
        assertEquals("hello over mesh", bobReceived.messages.single().body)
        assertEquals(1, aliceDelivered.receiptsApplied)
        assertEquals(MessageStatus.DELIVERED_TO_PEER, aliceDelivered.messages.single().status)
        assertEquals("sent=1 received=0 receipts=0 rejected=0", aliceSent.snapshot.lastSyncSummary)
        assertEquals("sent=0 received=1 receipts=0 rejected=0", bobReceived.snapshot.lastSyncSummary)
        assertEquals("sent=0 received=0 receipts=1 rejected=0", aliceDelivered.snapshot.lastSyncSummary)
    }

    @Test
    fun twoNodeServicesDeliverHandshakeConfirmationToPendingResponder() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val alicePeer = DiscoveredPeer("peer-alice", alice.fingerprint, alice.displayName)
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus)
        val bobTransport = InMemoryTwoNodeTransport(bobPeer, bus)
        aliceTransport.start()
        bobTransport.start()
        val aliceRepository = inMemoryRepository()
        val bobRepository = inMemoryRepository()
        val aliceService = MeshService(
            transportManager = TransportManager().apply { setForTest(aliceTransport) },
            repository = aliceRepository,
        )
        val bobService = MeshService(
            transportManager = TransportManager().apply { setForTest(bobTransport) },
            repository = bobRepository,
        )
        val aliceRelationship = relationship(alice, bob, RelationshipState.ACTIVE)
        val bobRelationship = relationship(bob, alice, RelationshipState.PENDING_HANDSHAKE)
        val confirmationPayload = OfflineHandshakeService()
            .generateConfirmationPayload(alice, aliceRelationship)
            .getOrThrow()
        val nowEpochMillis = System.currentTimeMillis()

        val queued = aliceService.enqueueHandshakeConfirmation(
            localIdentity = alice,
            relationship = aliceRelationship,
            confirmationPayloadJson = HandshakePayloadCodec.encodeConfirmation(confirmationPayload),
            nowEpochMillis = nowEpochMillis,
        )
        assertEquals("handshake-confirmation-queued", queued.lastPacketStatus)
        assertEquals(KrakenPacketType.HANDSHAKE_CONFIRMATION, aliceRepository.outboxStore.load().single().packet.packetType)
        assertEquals(PacketStoreStatus.QUEUED, aliceRepository.outboxStore.load().single().status)
        val aliceSent = aliceService.syncNow(
            localIdentity = alice,
            relationships = listOf(aliceRelationship),
            messages = emptyList(),
        )
        val bobConfirmed = bobService.syncNow(
            localIdentity = bob,
            relationships = listOf(bobRelationship),
            messages = emptyList(),
        )

        assertEquals(1, aliceSent.sentCount)
        assertEquals(PacketStoreStatus.SENT, aliceRepository.outboxStore.load().single().status)
        assertEquals(1, bobConfirmed.receivedCount)
        assertEquals(1, bobConfirmed.updatedRelationships.size)
        assertEquals(RelationshipState.ACTIVE, bobConfirmed.updatedRelationships.single().state)
        assertEquals(OfflineHandshakeRole.RESPONDER, bobConfirmed.updatedRelationships.single().offlineHandshakeRole)
        assertEquals("handshake-confirmed-1", bobConfirmed.snapshot.lastPacketStatus)
    }

    @Test
    fun twoNodeServicesCompleteDirectHandshakeResponseAndConfirmationWithoutSecondQr() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val alicePeer = DiscoveredPeer("peer-alice", alice.fingerprint, alice.displayName)
        val bobPeer = DiscoveredPeer("peer-bob", bob.fingerprint, bob.displayName)
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alicePeer, bus)
        val bobTransport = InMemoryTwoNodeTransport(bobPeer, bus)
        aliceTransport.start()
        bobTransport.start()
        val aliceRepository = inMemoryRepository()
        val bobRepository = inMemoryRepository()
        val aliceService = MeshService(
            transportManager = TransportManager().apply { setForTest(aliceTransport) },
            repository = aliceRepository,
        )
        val bobService = MeshService(
            transportManager = TransportManager().apply { setForTest(bobTransport) },
            repository = bobRepository,
        )
        val bobPendingRelationship = handshakeRelationship(
            local = bob,
            peer = alice,
            state = RelationshipState.PENDING_HANDSHAKE,
            role = OfflineHandshakeRole.RESPONDER,
        )
        val responsePayload = OfflineHandshakeService()
            .generateResponsePayload(bob, bobPendingRelationship)
            .getOrThrow()
        val issuedInvite = IssuedInviteRecord(
            inviteId = "invite-1",
            inviterFingerprint = alice.fingerprint,
            createdAtEpochMillis = 1_700_000_000_000,
        )

        val queuedResponse = bobService.enqueueHandshakeResponse(
            localIdentity = bob,
            relationship = bobPendingRelationship,
            responsePayloadJson = HandshakePayloadCodec.encodeResponse(responsePayload),
        )
        assertEquals("handshake-response-queued", queuedResponse.lastPacketStatus)
        assertEquals(KrakenPacketType.HANDSHAKE_RESPONSE, bobRepository.outboxStore.load().single().packet.packetType)

        val bobSentResponse = bobService.syncNow(
            localIdentity = bob,
            relationships = listOf(bobPendingRelationship),
            messages = emptyList(),
        )
        val aliceAcceptedResponse = aliceService.syncNow(
            localIdentity = alice,
            relationships = emptyList(),
            messages = emptyList(),
            issuedInvites = listOf(issuedInvite),
        )
        val bobConfirmed = bobService.syncNow(
            localIdentity = bob,
            relationships = listOf(bobPendingRelationship),
            messages = emptyList(),
        )

        assertEquals(1, bobSentResponse.sentCount)
        assertEquals(PacketStoreStatus.SENT, bobRepository.outboxStore.load().single().status)
        assertEquals(1, aliceAcceptedResponse.receivedCount)
        assertEquals(RelationshipState.ACTIVE, aliceAcceptedResponse.updatedRelationships.single().state)
        assertEquals(OfflineHandshakeRole.INVITER, aliceAcceptedResponse.updatedRelationships.single().offlineHandshakeRole)
        assertEquals(KrakenPacketType.HANDSHAKE_CONFIRMATION, aliceRepository.outboxStore.load().single().packet.packetType)
        assertEquals(PacketStoreStatus.SENT, aliceRepository.outboxStore.load().single().status)
        assertEquals(1, bobConfirmed.receivedCount)
        assertEquals(RelationshipState.ACTIVE, bobConfirmed.updatedRelationships.single().state)
        assertEquals(OfflineHandshakeRole.RESPONDER, bobConfirmed.updatedRelationships.single().offlineHandshakeRole)
    }

    @Test
    fun sentHandshakeResponseRetriesUntilInviterReceivesIt() {
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")
        val bobPeer = DiscoveredPeer("peer-alice", alice.fingerprint, alice.displayName)
        val bobTransport = FakePeerTransport(bob.fingerprint, listOf(bobPeer))
        val bobRepository = inMemoryRepository()
        val bobService = MeshService(
            transportManager = TransportManager().apply { setForTest(bobTransport) },
            repository = bobRepository,
        )
        val bobPendingRelationship = handshakeRelationship(
            local = bob,
            peer = alice,
            state = RelationshipState.PENDING_HANDSHAKE,
            role = OfflineHandshakeRole.RESPONDER,
        )
        val responsePayload = OfflineHandshakeService()
            .generateResponsePayload(bob, bobPendingRelationship)
            .getOrThrow()

        bobService.enqueueHandshakeResponse(
            localIdentity = bob,
            relationship = bobPendingRelationship,
            responsePayloadJson = HandshakePayloadCodec.encodeResponse(responsePayload),
        )
        bobService.syncNow(
            localIdentity = bob,
            relationships = listOf(bobPendingRelationship),
            messages = emptyList(),
        )
        val sentOnce = bobRepository.outboxStore.load().single()
        bobRepository.outboxStore.upsert(sentOnce.copy(nextAttemptAtEpochMillis = 0))

        val retryResult = bobService.syncNow(
            localIdentity = bob,
            relationships = listOf(bobPendingRelationship),
            messages = emptyList(),
        )

        assertEquals(1, retryResult.sentCount)
        assertEquals(2, bobTransport.sentPackets.size)
        assertEquals(sentOnce.packet.packetId, bobTransport.sentPackets[1].packetId)
        assertEquals(PacketStoreStatus.SENT, bobRepository.outboxStore.load().single().status)
        assertEquals(2, bobRepository.outboxStore.load().single().attempts)
    }

    private fun identity(
        id: String = "alice",
        name: String = "Alice",
        fingerprint: String = "ALICE-FP",
    ): LocalIdentity =
        LocalIdentity(
            identityId = id,
            displayName = name,
            publicKeyEncoded = "placeholder-pub:$id",
            privateKeyReference = "placeholder-private-ref:$id",
            fingerprint = fingerprint,
            createdAtEpochMillis = 1_700_000_000_000,
        )

    private fun message(
        status: MessageStatus,
        relationshipId: String = "relationship-1",
        peerFingerprint: String = "BOB-FP",
    ): LocalMessage =
        LocalMessage(
            messageId = "message-$status",
            conversationId = "conversation-1",
            relationshipId = relationshipId,
            peerFingerprint = peerFingerprint,
            direction = MessageDirection.OUTGOING,
            status = status,
            body = "hello",
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
        )

    private fun relationship(
        local: LocalIdentity,
        peer: LocalIdentity,
        state: RelationshipState,
        realmId: String? = null,
    ): Relationship =
        Relationship(
            relationshipId = "relationship-1",
            localIdentityPublicKey = local.publicKeyEncoded,
            peerPublicKey = peer.publicKeyEncoded,
            peerDisplayName = peer.displayName,
            peerFingerprint = peer.fingerprint,
            realmId = realmId,
            state = state,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
            sourceInviteId = "invite-1",
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
        )

    private fun handshakeRelationship(
        local: LocalIdentity,
        peer: LocalIdentity,
        state: RelationshipState,
        role: OfflineHandshakeRole,
    ): Relationship =
        Relationship(
            relationshipId = RelationshipService.offlineHandshakeRelationshipId(
                inviteId = "invite-1",
                inviterFingerprint = if (role == OfflineHandshakeRole.INVITER) local.fingerprint else peer.fingerprint,
                responderFingerprint = if (role == OfflineHandshakeRole.INVITER) peer.fingerprint else local.fingerprint,
            ),
            localIdentityPublicKey = local.publicKeyEncoded,
            peerPublicKey = peer.publicKeyEncoded,
            peerDisplayName = peer.displayName,
            peerFingerprint = peer.fingerprint,
            realmId = null,
            state = state,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
            sourceInviteId = "invite-1",
            offlineHandshakeRole = role,
        )

    private fun receiptPacket(
        sender: LocalIdentity,
        recipient: LocalIdentity,
        relationship: Relationship,
        originalPacket: KrakenPacket,
    ): KrakenPacket =
        KrakenPacket(
            packetId = "packet-receipt",
            packetType = KrakenPacketType.RECEIPT,
            senderFingerprint = sender.fingerprint,
            recipientFingerprint = recipient.fingerprint,
            relationshipId = relationship.relationshipId,
            conversationId = originalPacket.conversationId,
            messageId = originalPacket.messageId,
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = Long.MAX_VALUE,
            ttlHops = 4,
            payloadType = PacketPayloadType.RECEIPT_JSON,
            payloadJson = """{"packet_id":"${originalPacket.packetId}","message_id":"${originalPacket.messageId}"}""",
            cryptoProfileId = originalPacket.cryptoProfileId,
            sessionProfileId = originalPacket.sessionProfileId,
            admissionDecisionHash = originalPacket.admissionDecisionHash,
            profilePolicyVersion = originalPacket.profilePolicyVersion,
        )

    private fun standardSessionProfileId(relationship: Relationship): String =
        "session-${relationship.relationshipId}-${relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID}"

    private fun realmSnapshot(certificates: List<MembershipCertificate>): RealmSnapshot =
        RealmSnapshot(
            realms = listOf(
                Realm(
                    realmId = REALM_ID,
                    name = "OctoLab",
                    description = null,
                    createdByPublicKey = "placeholder-pub:alice",
                    createdAtEpochMillis = 1_700_000_000_000,
                    policy = RealmPolicy(),
                    capacityState = CapacityState(memberCount = certificates.size, capacity = 50, epoch = 1),
                    localState = LocalRealmState.ACTIVE,
                ),
            ),
            membershipCertificates = certificates,
            inviteEdges = emptyList(),
            pendingRequests = emptyList(),
        )

    private fun certificate(identity: LocalIdentity, capabilities: List<String>): MembershipCertificate =
        MembershipCertificate(
            realmId = REALM_ID,
            membershipId = "membership-${identity.identityId}",
            memberPublicKey = identity.publicKeyEncoded,
            issuedByPublicKey = "placeholder-pub:alice",
            issuedAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = Long.MAX_VALUE,
            capabilities = capabilities,
        )

    private companion object {
        const val REALM_ID = "realm-1"
    }

    private class FakePeerTransport(
        private val localFingerprint: String,
        private val peers: List<DiscoveredPeer>,
        inbound: List<ReceivedPacket> = emptyList(),
        private val diagnostics: MeshTransportDiagnostics = MeshTransportDiagnostics(discoveredPeerCount = peers.size),
        var failSends: Boolean = false,
        private val failureError: String? = null,
    ) : PeerTransport {
        val sentPeers = mutableListOf<DiscoveredPeer>()
        val sentPackets = mutableListOf<KrakenPacket>()
        private val inboundPackets = inbound.toMutableList()

        override fun start() = Unit
        override fun stop() = Unit
        override fun observePeers(): List<DiscoveredPeer> = peers
        override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult {
            sentPeers += peer
            sentPackets += packet
            return if (failSends || peer.fingerprint == localFingerprint) {
                TransportSendResult(false, failureError)
            } else {
                TransportSendResult(true)
            }
        }
        override fun observePackets(): List<ReceivedPacket> {
            val packets = inboundPackets.toList()
            inboundPackets.clear()
            return packets
        }

        override fun diagnostics(): MeshTransportDiagnostics = diagnostics
    }

    private class FakeManualLanTransport(
        private val localFingerprint: String,
    ) : PeerTransport, ManualPeerTransport {
        private val peers = mutableListOf<DiscoveredPeer>()

        override fun start() = Unit
        override fun stop() = Unit
        override fun observePeers(): List<DiscoveredPeer> = peers.toList()
        override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult =
            TransportSendResult(peer.fingerprint != localFingerprint)
        override fun observePackets(): List<ReceivedPacket> = emptyList()
        override fun diagnostics(): MeshTransportDiagnostics =
            MeshTransportDiagnostics(discoveredPeerCount = peers.size, manualPeerCount = peers.size)

        override fun addManualPeer(
            fingerprint: String,
            host: String,
            port: Int,
            displayName: String?,
        ): TransportSendResult {
            if (fingerprint == localFingerprint) return TransportSendResult(false, "self-fingerprint")
            peers += DiscoveredPeer(
                peerId = "manual-$host:$port",
                fingerprint = fingerprint,
                displayName = displayName,
            )
            return TransportSendResult(true)
        }
    }

    private fun inMemoryRepository(): MeshRepository =
        MeshRepository(
            outboxStore = InMemoryOutbox(),
            inboxStore = InMemoryInbox(),
            seenStore = InMemorySeen(),
            receiptStore = InMemoryReceipts(),
        )

    private class InMemoryOutbox : PacketOutbox {
        private var packets = emptyList<StoredPacket>()

        override fun load(): List<StoredPacket> = packets

        override fun upsert(packet: StoredPacket): List<StoredPacket> {
            packets = PacketStoragePolicy.pruneStoredPackets(
                packets.filterNot { it.packet.packetId == packet.packet.packetId } + packet,
                PacketStoragePolicy.MAX_OUTBOX_PACKETS,
            )
            return packets
        }

        override fun markStatus(
            packetId: String,
            status: PacketStoreStatus,
            lastError: MeshRejectionReason?,
        ): List<StoredPacket> {
            packets = PacketStoragePolicy.updateStoredPacketStatus(packets, packetId, status, lastError)
            return packets
        }

        override fun recordAttempt(
            packetId: String,
            status: PacketStoreStatus,
            nowEpochMillis: Long,
            nextAttemptAtEpochMillis: Long,
            lastError: MeshRejectionReason?,
        ): List<StoredPacket> {
            packets = PacketStoragePolicy.updateStoredPacketAttempt(
                packets = packets,
                packetId = packetId,
                status = status,
                nowEpochMillis = nowEpochMillis,
                nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
                lastError = lastError,
            )
            return packets
        }

        override fun queuedReceipts(): List<StoredPacket> =
            PacketStoragePolicy.queuedReceiptPackets(packets)

        override fun eligibleHandshakeResponses(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket> =
            PacketStoragePolicy.eligibleHandshakeResponsePackets(packets, nowEpochMillis, retryPolicy)

        override fun eligibleHandshakeConfirmations(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket> =
            PacketStoragePolicy.eligibleHandshakeConfirmationPackets(packets, nowEpochMillis, retryPolicy)

        override fun eligibleOutgoingMessages(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket> =
            PacketStoragePolicy.eligibleOutgoingMessagePackets(packets, nowEpochMillis, retryPolicy)

        override fun terminalOutgoingMessages(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket> =
            PacketStoragePolicy.terminalOutgoingMessagePackets(packets, nowEpochMillis, retryPolicy)

        override fun hasNonFinalMessagePacket(messageId: String): Boolean =
            PacketStoragePolicy.hasNonFinalMessagePacket(packets, messageId)

        override fun requeueMessagePackets(messageId: String, nowEpochMillis: Long): List<StoredPacket> {
            packets = packets.map { stored ->
                if (stored.packet.packetType == KrakenPacketType.MESSAGE && stored.packet.messageId == messageId) {
                    stored.copy(
                        status = PacketStoreStatus.QUEUED,
                        lastError = null,
                        nextAttemptAtEpochMillis = 0,
                        lastAttemptAtEpochMillis = nowEpochMillis,
                    )
                } else {
                    stored
                }
            }
            return packets
        }

        override fun deleteMessagePackets(messageId: String): List<StoredPacket> {
            packets = PacketStoragePolicy.deleteMessagePackets(packets, messageId)
            return packets
        }

        override fun deleteConversationPackets(conversationId: String): List<StoredPacket> {
            packets = PacketStoragePolicy.deleteConversationPackets(packets, conversationId)
            return packets
        }
    }

    private class InMemoryInbox : PacketInbox {
        private var packets = emptyList<StoredPacket>()
        override fun add(packet: StoredPacket): List<StoredPacket> {
            packets += packet
            return packets
        }
    }

    private class InMemorySeen : PacketSeen {
        private val seen = mutableSetOf<String>()
        override fun contains(packetId: String): Boolean = packetId in seen
        override fun markSeen(packetId: String, nowEpochMillis: Long): List<SeenPacketId> {
            seen += packetId
            return seen.map { SeenPacketId(it, nowEpochMillis) }
        }
    }

    private class InMemoryReceipts : PacketReceipts {
        private var receipts = emptyList<PacketReceipt>()
        override fun add(receipt: PacketReceipt): List<PacketReceipt> {
            receipts = PacketStoragePolicy.pruneReceipts(receipts + receipt)
            return receipts
        }
    }
}
