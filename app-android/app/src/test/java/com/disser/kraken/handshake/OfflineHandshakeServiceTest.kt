package com.disser.kraken.handshake

import com.disser.kraken.crypto.AdamovaNativeValidator
import com.disser.kraken.crypto.CryptoProfileKind
import com.disser.kraken.crypto.CryptoProfileRegistry
import com.disser.kraken.crypto.DefaultCryptoProfileRegistry
import com.disser.kraken.crypto.KrakenCryptoProfile
import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InviteImportResult
import com.disser.kraken.invite.InviteImportService
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.invite.InvitePayloadFactory
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.invite.PendingInviteState
import com.disser.kraken.nativecore.NativeAdamovaResult
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class OfflineHandshakeServiceTest {
    private val service = OfflineHandshakeService()
    private val alice = identity("alice", "Alice", "placeholder-pub:alice")
    private val bob = identity("bob", "Bob", "placeholder-pub:bob")
    private val bobPendingAlice = RelationshipService.createFromPendingInvite(
        localIdentity = bob,
        pendingInvite = PendingInviteImport(
            localId = "pending-alice",
            inviteId = "invite-alice-1",
            inviterDisplayName = alice.displayName,
            inviterPublicKeyEncoded = alice.publicKeyEncoded,
            inviterFingerprint = alice.fingerprint,
            importedAtEpochMillis = 1_700_000_000_000,
            state = PendingInviteState.PENDING_IMPORT,
        ),
        nowEpochMillis = 1_700_000_000_100,
    )

    @Test
    fun responsePayloadCanBeGeneratedForPendingImportedRelationship() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()

        assertEquals(HandshakeResponsePayload.TYPE, response.type)
        assertEquals("invite-alice-1", response.inviteId)
        assertEquals(bob.fingerprint, response.responderFingerprint)
        assertEquals(alice.fingerprint, response.inviterFingerprint)
        assertEquals("offline-qr-handshake-check-v1", response.proofPlaceholder)
    }

    @Test
    fun responsePayloadCarriesCryptoProfileBinding() {
        val pending = bobPendingAlice.copy(
            cryptoProfileId = KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID,
            cryptoProfileHash = KrakenCryptoProfileDefaults.STANDARD_PROFILE_HASH,
            admissionDecisionHash = KrakenCryptoProfileDefaults.STANDARD_ADMISSION_DECISION_HASH,
            profilePolicyVersion = KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION,
            nativeBackendVersion = KrakenCryptoProfileDefaults.STANDARD_NATIVE_BACKEND_VERSION,
        )

        val response = service.generateResponsePayload(bob, pending).getOrThrow()

        assertEquals(KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID, response.cryptoProfileId)
        assertEquals(KrakenCryptoProfileDefaults.STANDARD_PROFILE_HASH, response.cryptoProfileHash)
        assertEquals(KrakenCryptoProfileDefaults.STANDARD_ADMISSION_DECISION_HASH, response.admissionDecisionHash)
        assertEquals(KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION, response.profilePolicyVersion)
    }

    @Test
    fun acceptedExperimentalProfileMetadataRoundTripsThroughInviteResponseAndConfirmation() {
        val profile = experimentalProfile()
        val gate = ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(nativeResult(classificationCase = "A4")),
            registry = SingleProfileRegistry(profile),
            now = { 1_700_000_000_000 },
        )
        val admission = gate.evaluate(profile)
        val invite = InvitePayloadFactory.createForProfile(
            identity = alice,
            cryptoProfile = profile,
            admissionGate = gate,
            nowEpochMillis = 1_700_000_000_000,
        )
        val importResult = InviteImportService().import(
            rawJson = InvitePayloadCodec.encode(invite),
            localIdentity = bob,
            existingImports = emptyList(),
        ) as InviteImportResult.Success
        val bobPending = RelationshipService.createFromPendingInvite(
            localIdentity = bob,
            pendingInvite = importResult.pendingImport,
            nowEpochMillis = 1_700_000_000_100,
        )

        val response = OfflineHandshakeService(gate).generateResponsePayload(bob, bobPending).getOrThrow()
        val ownerResult = OfflineHandshakeService(gate).processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = response,
            knownInviteLifecycle = KnownInviteLifecycle(inviteId = invite.inviteId),
            nowEpochMillis = 1_700_000_001_000,
        ) as OfflineHandshakeResult.ResponseAccepted
        val responderResult = OfflineHandshakeService(gate).processConfirmationPayload(
            localIdentity = bob,
            relationships = listOf(bobPending.copy(state = RelationshipState.PENDING_HANDSHAKE)),
            payload = ownerResult.confirmationPayload,
            nowEpochMillis = 1_700_000_002_000,
        ) as OfflineHandshakeResult.ConfirmationAccepted

        assertEquals(profile.profileId, invite.cryptoProfileId)
        assertEquals(admission.profileHash, invite.cryptoProfileHash)
        assertEquals(admission.decisionHash, invite.admissionDecisionHash)
        assertEquals(admission.policyVersion, invite.profilePolicyVersion)
        assertEquals(admission.nativeBackendVersion, invite.nativeBackendVersion)
        assertEquals(profile.profileId, response.cryptoProfileId)
        assertEquals(admission.decisionHash, response.admissionDecisionHash)
        assertEquals(profile.profileId, ownerResult.relationship.cryptoProfileId)
        assertEquals(admission.decisionHash, ownerResult.relationship.admissionDecisionHash)
        assertEquals(profile.profileId, ownerResult.confirmationPayload.cryptoProfileId)
        assertEquals(RelationshipState.ACTIVE, responderResult.relationship.state)
        assertEquals(profile.profileId, responderResult.relationship.cryptoProfileId)
        assertEquals(admission.decisionHash, responderResult.relationship.admissionDecisionHash)
    }

    @Test
    fun weakExperimentalProfileCannotCreateMessageCapableInvite() {
        val profile = experimentalProfile(profileId = "experimental-weak-two-torsion-v1", a = "-1", b = "0")
        val gate = ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(nativeResult(twoTorsionRootCount = 1, classificationCase = "A5")),
            registry = SingleProfileRegistry(profile),
            now = { 1_700_000_000_000 },
        )

        val result = runCatching {
            InvitePayloadFactory.createForProfile(
                identity = alice,
                cryptoProfile = profile,
                admissionGate = gate,
                nowEpochMillis = 1_700_000_000_000,
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("rejected by Adamova admission gate"))
    }

    @Test
    fun realmScopedPendingRelationshipCarriesRealmIntoResponseAndConfirmation() {
        val realmPending = bobPendingAlice.copy(realmId = "realm-1")
        val response = service.generateResponsePayload(bob, realmPending).getOrThrow()
        val ownerResult = service.processResponsePayload(alice, emptyList(), response) as OfflineHandshakeResult.ResponseAccepted

        assertEquals("realm-1", response.realmId)
        assertTrue(response.requiresApproval)
        assertEquals("realm-1", ownerResult.relationship.realmId)
        assertEquals("realm-1", ownerResult.confirmationPayload.realmId)
    }

    @Test
    fun responsePayloadRoundTripsThroughCodec() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()
        val encoded = HandshakePayloadCodec.encodeResponse(response)

        assertEquals(HandshakePayloadKind.RESPONSE, HandshakePayloadCodec.detectKind(encoded))
        assertEquals(response, HandshakePayloadCodec.decodeResponse(encoded).getOrThrow())
    }

    @Test
    fun processingValidResponseActivatesInviteOwnerSide() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()

        val result = service.processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = response,
            knownInviteLifecycle = KnownInviteLifecycle(inviteId = "invite-alice-1"),
            nowEpochMillis = 1_700_000_001_000,
        )

        assertTrue(result is OfflineHandshakeResult.ResponseAccepted)
        val accepted = result as OfflineHandshakeResult.ResponseAccepted
        assertEquals(RelationshipState.ACTIVE, accepted.relationship.state)
        assertEquals(response.relationshipHint, accepted.relationship.relationshipId)
        assertEquals(OfflineHandshakeRole.INVITER, accepted.relationship.offlineHandshakeRole)
        assertEquals(bob.fingerprint, accepted.relationship.peerFingerprint)
        assertEquals("invite-alice-1", accepted.relationship.sourceInviteId)
        assertEquals(HandshakeConfirmationPayload.TYPE, accepted.confirmationPayload.type)
    }

    @Test
    fun responseWithUnknownExperimentalCryptoProfileDoesNotActivateOwnerSide() {
        val response = service.generateResponsePayload(
            bob,
            bobPendingAlice.copy(
                cryptoProfileId = "unknown-experimental-profile",
                cryptoProfileHash = "sha256:unknown",
                admissionDecisionHash = "sha256:unknown-decision",
                profilePolicyVersion = 1,
                nativeBackendVersion = "unknown-native",
            ),
        ).getOrThrow()

        val result = service.processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = response,
            knownInviteLifecycle = KnownInviteLifecycle(inviteId = "invite-alice-1"),
            nowEpochMillis = 1_700_000_001_000,
        )

        assertTrue(result is OfflineHandshakeResult.Error)
        assertTrue((result as OfflineHandshakeResult.Error).reason.contains("Криптографический профиль"))
    }

    @Test
    fun finalConfirmationActivatesResponderSide() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()
        val ownerResult = service.processResponsePayload(alice, emptyList(), response) as OfflineHandshakeResult.ResponseAccepted

        val responderResult = service.processConfirmationPayload(
            localIdentity = bob,
            relationships = listOf(bobPendingAlice.copy(state = RelationshipState.PENDING_HANDSHAKE)),
            payload = ownerResult.confirmationPayload,
        )

        assertTrue(responderResult is OfflineHandshakeResult.ConfirmationAccepted)
        val accepted = responderResult as OfflineHandshakeResult.ConfirmationAccepted
        assertEquals(RelationshipState.ACTIVE, accepted.relationship.state)
        assertEquals(ownerResult.relationship.relationshipId, accepted.relationship.relationshipId)
        assertEquals(OfflineHandshakeRole.RESPONDER, accepted.relationship.offlineHandshakeRole)
    }

    @Test
    fun finalConfirmationWithDifferentCryptoProfileDoesNotActivateResponderSide() {
        val pending = bobPendingAlice.copy(
            state = RelationshipState.PENDING_HANDSHAKE,
            cryptoProfileId = KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID,
            cryptoProfileHash = KrakenCryptoProfileDefaults.STANDARD_PROFILE_HASH,
            admissionDecisionHash = KrakenCryptoProfileDefaults.STANDARD_ADMISSION_DECISION_HASH,
            profilePolicyVersion = KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION,
        )
        val response = service.generateResponsePayload(bob, pending).getOrThrow()
        val ownerResult = service.processResponsePayload(alice, emptyList(), response) as OfflineHandshakeResult.ResponseAccepted
        val confirmation = ownerResult.confirmationPayload.copy(
            cryptoProfileId = "unknown-experimental-profile",
            cryptoProfileHash = "sha256:unknown",
            admissionDecisionHash = "sha256:unknown-decision",
            profilePolicyVersion = 1,
        )

        val result = service.processConfirmationPayload(
            localIdentity = bob,
            relationships = listOf(pending),
            payload = confirmation,
        )

        assertTrue(result is OfflineHandshakeResult.Error)
        assertTrue((result as OfflineHandshakeResult.Error).reason.contains("Криптографический профиль"))
    }

    @Test
    fun finalConfirmationCanCarryRealmMembershipCertificate() {
        val realmPending = bobPendingAlice.copy(realmId = "realm-1", state = RelationshipState.PENDING_HANDSHAKE)
        val response = service.generateResponsePayload(bob, realmPending).getOrThrow()
        val ownerResult = service.processResponsePayload(alice, emptyList(), response) as OfflineHandshakeResult.ResponseAccepted
        val certificate = membershipCertificate(
            realmId = "realm-1",
            memberPublicKey = bob.publicKeyEncoded,
            issuedByPublicKey = alice.publicKeyEncoded,
        )
        val confirmation = service.generateConfirmationPayload(
            localIdentity = alice,
            relationship = ownerResult.relationship,
            realmName = "OctoLab",
            membershipCertificate = certificate,
        ).getOrThrow()

        val responderResult = service.processConfirmationPayload(
            localIdentity = bob,
            relationships = listOf(realmPending),
            payload = confirmation,
        )

        assertTrue(responderResult is OfflineHandshakeResult.ConfirmationAccepted)
        assertEquals("realm-1", confirmation.realmId)
        assertEquals("OctoLab", confirmation.realmName)
        assertEquals(certificate, confirmation.membershipCertificate)
    }

    @Test
    fun finalConfirmationRejectsMembershipCertificateForAnotherIdentity() {
        val realmPending = bobPendingAlice.copy(realmId = "realm-1", state = RelationshipState.PENDING_HANDSHAKE)
        val response = service.generateResponsePayload(bob, realmPending).getOrThrow()
        val ownerResult = service.processResponsePayload(alice, emptyList(), response) as OfflineHandshakeResult.ResponseAccepted
        val confirmation = service.generateConfirmationPayload(
            localIdentity = alice,
            relationship = ownerResult.relationship,
            membershipCertificate = membershipCertificate(
                realmId = "realm-1",
                memberPublicKey = "placeholder-pub:mallory",
                issuedByPublicKey = alice.publicKeyEncoded,
            ),
        ).getOrThrow()

        val responderResult = service.processConfirmationPayload(
            localIdentity = bob,
            relationships = listOf(realmPending),
            payload = confirmation,
        )

        assertTrue(responderResult is OfflineHandshakeResult.Error)
        assertTrue((responderResult as OfflineHandshakeResult.Error).reason.contains("another identity"))
    }

    @Test
    fun selfHandshakeIsRejected() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()
            .copy(inviterFingerprint = bob.fingerprint)

        val result = service.processResponsePayload(bob, emptyList(), response)

        assertTrue(result is OfflineHandshakeResult.Error)
        assertTrue((result as OfflineHandshakeResult.Error).reason.contains("Self-handshake"))
    }

    @Test
    fun wrongRecipientIsRejected() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()
            .copy(inviterFingerprint = "FFFF 0000 WRONG")

        val result = service.processResponsePayload(alice, emptyList(), response)

        assertTrue(result is OfflineHandshakeResult.Error)
        assertTrue((result as OfflineHandshakeResult.Error).reason.contains("another identity"))
    }

    @Test
    fun duplicateResponseIsIdempotentForExistingActiveRelationship() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()
        val first = service.processResponsePayload(alice, emptyList(), response) as OfflineHandshakeResult.ResponseAccepted

        val second = service.processResponsePayload(alice, listOf(first.relationship), response)

        assertTrue(second is OfflineHandshakeResult.ResponseAccepted)
        assertTrue((second as OfflineHandshakeResult.ResponseAccepted).idempotent)
        assertEquals(first.relationship.relationshipId, second.relationship.relationshipId)
    }

    @Test
    fun invalidKnownInviteLifecycleDoesNotActivate() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()

        val unknown = service.processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = response,
            knownInviteLifecycle = KnownInviteLifecycle(inviteId = "invite-other"),
        )
        val revoked = service.processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = response,
            knownInviteLifecycle = KnownInviteLifecycle(inviteId = "invite-alice-1", revoked = true),
        )
        val expired = service.processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = response,
            knownInviteLifecycle = KnownInviteLifecycle(inviteId = "invite-alice-1", expiresAtEpochMillis = 1),
            nowEpochMillis = 2,
        )
        val consumed = service.processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = response,
            knownInviteLifecycle = KnownInviteLifecycle(inviteId = "invite-alice-1", consumed = true),
        )

        assertTrue(unknown is OfflineHandshakeResult.Error)
        assertTrue(revoked is OfflineHandshakeResult.Error)
        assertTrue(expired is OfflineHandshakeResult.Error)
        assertTrue(consumed is OfflineHandshakeResult.Error)
    }

    @Test
    fun confirmationForUnknownInviteFailsSafely() {
        val response = service.generateResponsePayload(bob, bobPendingAlice).getOrThrow()
        val confirmation = service.generateConfirmationPayload(alice, response)

        val result = service.processConfirmationPayload(bob, emptyList(), confirmation)

        assertTrue(result is OfflineHandshakeResult.Error)
        assertTrue((result as OfflineHandshakeResult.Error).reason.contains("No pending relationship"))
    }

    private fun identity(id: String, displayName: String, publicKey: String): LocalIdentity =
        LocalIdentity(
            identityId = id,
            displayName = displayName,
            publicKeyEncoded = publicKey,
            privateKeyReference = "placeholder-private-ref:$id",
            fingerprint = FingerprintFormatter.shortFingerprint(publicKey),
            createdAtEpochMillis = 1_700_000_000_000,
        )

    private fun experimentalProfile(
        profileId: String = "experimental-accepted-a4-v1",
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

    private class SingleProfileRegistry(private val profile: KrakenCryptoProfile) : CryptoProfileRegistry {
        override fun find(profileId: String): KrakenCryptoProfile? =
            when (profileId) {
                profile.profileId -> profile
                KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID -> DefaultCryptoProfileRegistry.standardProfile
                else -> null
            }
    }

    private class FakeAdamovaValidator(private val result: NativeAdamovaResult?) : AdamovaNativeValidator {
        override fun status(): String = "fake-native-adamova-v1"
        override fun classify(a: BigInteger, b: BigInteger): NativeAdamovaResult? = result
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

    private fun membershipCertificate(
        realmId: String,
        memberPublicKey: String,
        issuedByPublicKey: String,
    ): MembershipCertificate =
        MembershipCertificate(
            realmId = realmId,
            membershipId = "membership-$realmId",
            memberPublicKey = memberPublicKey,
            issuedByPublicKey = issuedByPublicKey,
            issuedAtEpochMillis = 1_700_000_000_900,
            expiresAtEpochMillis = null,
            capabilities = listOf("send_direct", "relay_basic"),
        )
}
