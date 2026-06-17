package com.disser.kraken.mesh

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshForegroundPolicyTest {
    private val alice = identity("alice", "Alice", "ALICE-FP")
    private val bob = identity("bob", "Bob", "BOB-FP")
    private val emptyRealmSnapshot = RealmSnapshot(
        realms = emptyList(),
        membershipCertificates = emptyList(),
        inviteEdges = emptyList(),
        pendingRequests = emptyList(),
    )

    @Test
    fun autoStartsOnlyWhenActiveQrContactExistsAndMeshIsOff() {
        assertTrue(
            MeshForegroundPolicy.shouldAutoStartLan(
                localIdentity = alice,
                relationships = listOf(relationship(RelationshipState.ACTIVE)),
                realmSnapshot = emptyRealmSnapshot,
                meshState = MeshState.OFF,
            )
        )
    }

    @Test
    fun doesNotAutoStartWithoutIdentityOrActiveContact() {
        assertFalse(
            MeshForegroundPolicy.shouldAutoStartLan(
                localIdentity = null,
                relationships = listOf(relationship(RelationshipState.ACTIVE)),
                realmSnapshot = emptyRealmSnapshot,
                meshState = MeshState.OFF,
            )
        )
        assertFalse(
            MeshForegroundPolicy.shouldAutoStartLan(
                localIdentity = alice,
                relationships = listOf(relationship(RelationshipState.PENDING_HANDSHAKE)),
                realmSnapshot = emptyRealmSnapshot,
                meshState = MeshState.OFF,
            )
        )
    }

    @Test
    fun doesNotRestartAlreadyRunningMesh() {
        assertFalse(
            MeshForegroundPolicy.shouldAutoStartLan(
                localIdentity = alice,
                relationships = listOf(relationship(RelationshipState.ACTIVE)),
                realmSnapshot = emptyRealmSnapshot,
                meshState = MeshState.SCANNING,
            )
        )
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

    private fun relationship(state: RelationshipState): Relationship =
        Relationship(
            relationshipId = "relationship-1",
            localIdentityPublicKey = alice.publicKeyEncoded,
            peerPublicKey = bob.publicKeyEncoded,
            peerDisplayName = bob.displayName,
            peerFingerprint = bob.fingerprint,
            realmId = null,
            state = state,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
            sourceInviteId = "invite-1",
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
        )
}
