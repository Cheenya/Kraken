package com.disser.kraken.relationship

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.invite.PendingInviteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipServiceTest {
    private val localIdentity = LocalIdentity(
        identityId = "local",
        displayName = "Alice",
        publicKeyEncoded = "placeholder-pub:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo",
        privateKeyReference = "placeholder-private-ref:local",
        fingerprint = FingerprintFormatter.shortFingerprint("placeholder-pub:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo"),
        createdAtEpochMillis = 1_700_000_000_000,
    )

    private val pendingInvite = PendingInviteImport(
        localId = "pending",
        inviteId = "invite-1",
        inviterDisplayName = "Bob",
        inviterPublicKeyEncoded = "placeholder-pub:QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI",
        inviterFingerprint = "AB12 CD34 EF56 7890",
        importedAtEpochMillis = 1_700_000_000_100,
        state = PendingInviteState.PENDING_IMPORT,
    )

    @Test
    fun importedInviteCreatesPendingImportRelationship() {
        val relationship = RelationshipService.createFromPendingInvite(localIdentity, pendingInvite)

        assertEquals(RelationshipState.PENDING_IMPORT, relationship.state)
        assertEquals(pendingInvite.inviteId, relationship.sourceInviteId)
    }

    @Test
    fun realmInviteCreatesRealmScopedPendingRelationship() {
        val relationship = RelationshipService.createFromPendingInvite(
            localIdentity,
            pendingInvite.copy(realmId = "realm-1", requiresApproval = true),
        )

        assertEquals("realm-1", relationship.realmId)
        assertEquals(RelationshipState.PENDING_IMPORT, relationship.state)
    }

    @Test
    fun handshakeMovesPendingImportToActive() {
        val pending = RelationshipService.createFromPendingInvite(localIdentity, pendingInvite)

        val handshaking = RelationshipService.startHandshake(pending)
        val active = RelationshipService.acceptHandshake(handshaking)

        assertEquals(RelationshipState.PENDING_HANDSHAKE, handshaking.state)
        assertEquals(RelationshipState.ACTIVE, active.state)
    }

    @Test
    fun canSendMessageOnlyInActiveState() {
        val pending = RelationshipService.createFromPendingInvite(localIdentity, pendingInvite)
        val active = RelationshipService.acceptHandshake(RelationshipService.startHandshake(pending))

        assertFalse(RelationshipService.canSendMessage(pending))
        assertTrue(RelationshipService.canSendMessage(active))
    }

    @Test
    fun neutralUnlinkCreatesNoComplaint() {
        val active = activeRelationship(realmId = "realm-demo")

        val result = RelationshipService.unlinkRelationship(active, UnlinkReason.ENDED_INTERACTION)

        assertEquals(RelationshipState.UNLINKED, result.relationship.state)
        assertNull(result.complaintEvent)
        assertFalse(RelationshipService.canSendMessage(result.relationship))
    }

    @Test
    fun negativeUnlinkWithRealmCreatesComplaint() {
        val active = activeRelationship(realmId = "realm-demo")

        val result = RelationshipService.unlinkRelationship(active, UnlinkReason.SPAM)

        assertEquals(RelationshipState.UNLINKED, result.relationship.state)
        assertEquals("realm-demo", result.complaintEvent?.realmId)
        assertEquals(UnlinkReason.SPAM, result.complaintEvent?.reason)
    }

    @Test
    fun peerUnlinkMovesActiveToBlockedByPeer() {
        val active = activeRelationship(realmId = null)
        val notice = UnlinkNotice(
            relationshipId = active.relationshipId,
            fromPublicKey = active.peerPublicKey,
            toPublicKey = active.localIdentityPublicKey,
            reason = UnlinkReason.OTHER,
            createdAtEpochMillis = 1_700_000_000_500,
        )

        val blocked = RelationshipService.applyPeerUnlinkNotice(active, notice)

        assertEquals(RelationshipState.BLOCKED_BY_PEER, blocked.state)
        assertFalse(RelationshipService.canSendMessage(blocked))
        assertTrue(RelationshipService.requiresRejoin(blocked))
    }

    @Test
    fun oldRelationshipCannotBeReactivatedDirectly() {
        val unlinked = activeRelationship(realmId = null).copy(state = RelationshipState.UNLINKED)

        val attempted = RelationshipService.acceptHandshake(unlinked)

        assertEquals(RelationshipState.UNLINKED, attempted.state)
        assertTrue(RelationshipService.requiresRejoin(attempted))
    }

    private fun activeRelationship(realmId: String?): Relationship =
        RelationshipService.acceptHandshake(
            RelationshipService.startHandshake(
                RelationshipService.createFromPendingInvite(localIdentity, pendingInvite)
            )
        ).copy(realmId = realmId)
}
