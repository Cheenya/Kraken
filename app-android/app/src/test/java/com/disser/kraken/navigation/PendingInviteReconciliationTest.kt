package com.disser.kraken.navigation

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.OneTimeInvitePayload
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.invite.PendingInviteState
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PendingInviteReconciliationTest {
    private val localIdentity = LocalIdentity(
        identityId = "local",
        displayName = "Alice",
        publicKeyEncoded = "placeholder-pub:local",
        privateKeyReference = "placeholder-private-ref:local",
        fingerprint = "1111 2222 3333 4444",
        createdAtEpochMillis = 1_700_000_000_000,
    )

    private val payload = OneTimeInvitePayload(
        inviteId = "invite-new",
        inviterDisplayName = "Xiaomi",
        inviterPublicKeyEncoded = "placeholder-pub:peer",
        inviterFingerprint = "AAAA BBBB CCCC DDDD",
        createdAtEpochMillis = 1_700_000_000_100,
        expiresAtEpochMillis = null,
        capabilities = listOf("kraken.invite.v1"),
    )

    @Test
    fun freshScanDropsStalePendingImportWhenRelationshipWasForgotten() {
        val stale = pendingImport("invite-old", payload.inviterPublicKeyEncoded, payload.inviterFingerprint)
        val unrelated = pendingImport("invite-other", "placeholder-pub:other", "EEEE FFFF 0000 1111")

        val reconciled = reconcilePendingInvitesForFreshInviteScan(
            existingImports = listOf(stale, unrelated),
            relationships = emptyList(),
            localIdentity = localIdentity,
            payload = payload,
        )

        assertEquals(listOf(unrelated), reconciled)
    }

    @Test
    fun freshScanKeepsPendingImportWhenKnownRelationshipStillExists() {
        val existing = pendingImport("invite-old", payload.inviterPublicKeyEncoded, payload.inviterFingerprint)
        val relationship = relationshipWithPeer(payload.inviterPublicKeyEncoded, payload.inviterFingerprint)
        val imports = listOf(existing)

        val reconciled = reconcilePendingInvitesForFreshInviteScan(
            existingImports = imports,
            relationships = listOf(relationship),
            localIdentity = localIdentity,
            payload = payload,
        )

        assertSame(imports, reconciled)
    }

    private fun pendingImport(
        inviteId: String,
        publicKey: String,
        fingerprint: String,
    ): PendingInviteImport =
        PendingInviteImport(
            localId = "pending-$inviteId",
            inviteId = inviteId,
            inviterDisplayName = "Peer",
            inviterPublicKeyEncoded = publicKey,
            inviterFingerprint = fingerprint,
            importedAtEpochMillis = 1_700_000_000_200,
            state = PendingInviteState.PENDING_IMPORT,
        )

    private fun relationshipWithPeer(
        publicKey: String,
        fingerprint: String,
    ): Relationship =
        Relationship(
            relationshipId = "relationship-existing",
            localIdentityPublicKey = localIdentity.publicKeyEncoded,
            peerPublicKey = publicKey,
            peerDisplayName = "Peer",
            peerFingerprint = fingerprint,
            realmId = null,
            state = RelationshipState.ACTIVE,
            createdAtEpochMillis = 1_700_000_000_300,
            updatedAtEpochMillis = 1_700_000_000_400,
            sourceInviteId = "invite-old",
        )
}
