package com.disser.kraken.realm

import com.disser.kraken.handshake.KnownInviteLifecycle
import com.disser.kraken.handshake.OfflineHandshakeResult
import com.disser.kraken.handshake.OfflineHandshakeService
import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InviteImportResult
import com.disser.kraken.invite.InviteImportService
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.invite.InvitePayloadFactory
import com.disser.kraken.invite.IssuedInviteRecord
import com.disser.kraken.invite.toKnownInviteLifecycle
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealmQrMembershipFlowTest {
    private val handshakeService = OfflineHandshakeService()
    private val inviteImportService = InviteImportService()
    private val alice = identity("alice", "Alice", "placeholder-pub:alice-owner")
    private val bob = identity("bob", "Bob", "placeholder-pub:bob-member")

    @Test
    fun realmInviteApprovalFinalQrAppliesMembershipOnResponderDevice() {
        val ownerCreation = RealmService.createRealm(
            owner = alice,
            name = "OctoLab",
            nowEpochMillis = 1_700_000_000_000,
        )
        val realmInvite = InvitePayloadFactory.createRealmInvite(
            identity = alice,
            realm = ownerCreation.realm,
            certificate = ownerCreation.membershipCertificate,
            nowEpochMillis = 1_700_000_000_100,
        )

        val bobImport = inviteImportService.import(
            rawJson = InvitePayloadCodec.encode(realmInvite),
            localIdentity = bob,
            existingImports = emptyList(),
        ) as InviteImportResult.Success
        val bobPendingRelationship = RelationshipService.createFromPendingInvite(
            localIdentity = bob,
            pendingInvite = bobImport.pendingImport,
            nowEpochMillis = 1_700_000_000_300,
        )

        assertEquals(RelationshipState.PENDING_IMPORT, bobPendingRelationship.state)
        assertEquals(ownerCreation.realm.realmId, bobPendingRelationship.realmId)

        val responsePayload = handshakeService.generateResponsePayload(
            localIdentity = bob,
            relationship = RelationshipService.startHandshake(bobPendingRelationship),
            nowEpochMillis = 1_700_000_000_400,
        ).getOrThrow()
        val ownerAccepted = handshakeService.processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = responsePayload,
            knownInviteLifecycle = KnownInviteLifecycle(inviteId = realmInvite.inviteId),
            nowEpochMillis = 1_700_000_000_500,
        ) as OfflineHandshakeResult.ResponseAccepted

        val pendingRequest = RealmService.createPendingMembershipRequest(
            realm = ownerCreation.realm,
            inviteId = responsePayload.inviteId,
            inviterPublicKey = alice.publicKeyEncoded,
            inviteePublicKey = responsePayload.responderPublicKeyEncoded,
            inviteeDisplayName = responsePayload.responderDisplayName,
            nowEpochMillis = responsePayload.createdAtEpochMillis,
        )
        val approval = ApprovalEvaluator.recordDecision(
            request = pendingRequest,
            decision = ApprovalDecision(
                approverPublicKey = alice.publicKeyEncoded,
                approverRole = RealmRole.OWNER,
                decisionType = ApprovalDecisionType.APPROVE,
                decidedAtEpochMillis = 1_700_000_000_600,
            ),
            issuedByPublicKey = alice.publicKeyEncoded,
            nowEpochMillis = 1_700_000_000_700,
        )
        val ownerSnapshot = RealmService.applyApprovalOutcome(
            snapshot = RealmSnapshot(
                realms = listOf(ownerCreation.realm),
                membershipCertificates = listOf(ownerCreation.membershipCertificate),
                inviteEdges = emptyList(),
                pendingRequests = listOf(pendingRequest),
            ),
            outcome = approval,
        )
        val issuedCertificate = ownerSnapshot.membershipCertificates.single {
            it.memberPublicKey == bob.publicKeyEncoded
        }

        val finalConfirmation = handshakeService.generateConfirmationPayload(
            localIdentity = alice,
            relationship = ownerAccepted.relationship,
            realmName = ownerCreation.realm.name,
            membershipCertificate = issuedCertificate,
            nowEpochMillis = 1_700_000_000_800,
        ).getOrThrow()
        val bobAccepted = handshakeService.processConfirmationPayload(
            localIdentity = bob,
            relationships = listOf(RelationshipService.startHandshake(bobPendingRelationship)),
            payload = finalConfirmation,
            nowEpochMillis = 1_700_000_000_900,
        ) as OfflineHandshakeResult.ConfirmationAccepted
        val bobSnapshot = RealmService.applyMembershipConfirmation(
            snapshot = RealmSnapshot(
                realms = emptyList(),
                membershipCertificates = emptyList(),
                inviteEdges = emptyList(),
                pendingRequests = emptyList(),
            ),
            realmName = finalConfirmation.realmName,
            inviteId = finalConfirmation.inviteId,
            inviterPublicKey = bobAccepted.relationship.peerPublicKey,
            certificate = requireNotNull(finalConfirmation.membershipCertificate),
        )

        assertEquals(RelationshipState.ACTIVE, bobAccepted.relationship.state)
        assertEquals(ownerCreation.realm.realmId, bobSnapshot.realms.single().realmId)
        assertEquals("OctoLab", bobSnapshot.realms.single().name)
        assertEquals(LocalRealmState.ACTIVE, bobSnapshot.realms.single().localState)
        assertEquals(bob.publicKeyEncoded, bobSnapshot.membershipCertificates.single().memberPublicKey)
        assertEquals(alice.publicKeyEncoded, bobSnapshot.inviteEdges.single().inviterPublicKey)
        assertEquals(realmInvite.inviteId, bobSnapshot.inviteEdges.single().inviteId)
        assertNotNull(finalConfirmation.membershipCertificate)
        assertTrue(finalConfirmation.proofPlaceholder.contains("not-production-crypto"))
    }

    @Test
    fun consumedRealmInviteResponseIsRejectedBeforeOwnerCreatesMembershipRequest() {
        val ownerCreation = RealmService.createRealm(
            owner = alice,
            name = "OctoLab",
            nowEpochMillis = 1_700_000_000_000,
        )
        val realmInvite = InvitePayloadFactory.createRealmInvite(
            identity = alice,
            realm = ownerCreation.realm,
            certificate = ownerCreation.membershipCertificate,
            nowEpochMillis = 1_700_000_000_100,
        )
        val bobImport = inviteImportService.import(
            rawJson = InvitePayloadCodec.encode(realmInvite),
            localIdentity = bob,
            existingImports = emptyList(),
        ) as InviteImportResult.Success
        val bobPendingRelationship = RelationshipService.createFromPendingInvite(
            localIdentity = bob,
            pendingInvite = bobImport.pendingImport,
            nowEpochMillis = 1_700_000_000_300,
        )
        val responsePayload = handshakeService.generateResponsePayload(
            localIdentity = bob,
            relationship = RelationshipService.startHandshake(bobPendingRelationship),
            nowEpochMillis = 1_700_000_000_400,
        ).getOrThrow()
        val consumedLifecycle = IssuedInviteRecord.fromPayload(realmInvite)
            .copy(consumed = true)
            .toKnownInviteLifecycle()

        val ownerResult = handshakeService.processResponsePayload(
            localIdentity = alice,
            relationships = emptyList(),
            payload = responsePayload,
            knownInviteLifecycle = consumedLifecycle,
            nowEpochMillis = 1_700_000_000_500,
        )

        assertTrue(ownerResult is OfflineHandshakeResult.Error)
        assertTrue((ownerResult as OfflineHandshakeResult.Error).reason.contains("already consumed"))
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
}
