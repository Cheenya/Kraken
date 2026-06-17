package com.disser.kraken.realm

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealmCommunicationPolicyTest {
    private val alice = identity("alice", "ALICE-FP")
    private val bob = identity("bob", "BOB-FP")
    private val realm = realm()

    @Test
    fun directActiveRelationshipDoesNotRequireRealmCertificate() {
        val decision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = alice,
            relationship = relationship(realmId = null),
            realmSnapshot = null,
            nowEpochMillis = NOW,
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun realmRelationshipRequiresLocalAndPeerSendDirectCertificates() {
        val decision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = alice,
            relationship = relationship(),
            realmSnapshot = snapshot(
                certificates = listOf(
                    certificate(alice, listOf("send_direct", "admin")),
                    certificate(bob, listOf("send_direct")),
                ),
            ),
            nowEpochMillis = NOW,
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun removedPeerCertificateBlocksRealmRelationship() {
        val decision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = alice,
            relationship = relationship(),
            realmSnapshot = snapshot(certificates = listOf(certificate(alice, listOf("send_direct")))),
            nowEpochMillis = NOW,
        )

        assertFalse(decision.allowed)
        assertEquals(RealmCommunicationBlockReason.PEER_MEMBERSHIP_MISSING, decision.blockReason)
    }

    @Test
    fun restrictedPeerCertificateBlocksRealmRelationship() {
        val decision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = alice,
            relationship = relationship(),
            realmSnapshot = snapshot(
                certificates = listOf(
                    certificate(alice, listOf("send_direct")),
                    certificate(bob, listOf("restricted")),
                ),
            ),
            nowEpochMillis = NOW,
        )

        assertFalse(decision.allowed)
        assertEquals(RealmCommunicationBlockReason.PEER_MEMBERSHIP_RESTRICTED, decision.blockReason)
    }

    @Test
    fun archivedRealmBlocksRealmRelationship() {
        val decision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = alice,
            relationship = relationship(),
            realmSnapshot = snapshot(
                realm = realm.copy(localState = LocalRealmState.ARCHIVED),
                certificates = listOf(
                    certificate(alice, listOf("send_direct")),
                    certificate(bob, listOf("send_direct")),
                ),
            ),
            nowEpochMillis = NOW,
        )

        assertFalse(decision.allowed)
        assertEquals(RealmCommunicationBlockReason.REALM_NOT_ACTIVE, decision.blockReason)
    }

    private fun identity(id: String, fingerprint: String): LocalIdentity =
        LocalIdentity(
            identityId = id,
            displayName = id.replaceFirstChar { it.uppercase() },
            publicKeyEncoded = "placeholder-pub:$id",
            privateKeyReference = "placeholder-private-ref:$id",
            fingerprint = fingerprint,
            createdAtEpochMillis = NOW,
        )

    private fun realm(): Realm =
        Realm(
            realmId = REALM_ID,
            name = "OctoLab",
            description = null,
            createdByPublicKey = alice.publicKeyEncoded,
            createdAtEpochMillis = NOW,
            policy = RealmPolicy(),
            capacityState = CapacityState(memberCount = 2, capacity = 50, epoch = 1),
            localState = LocalRealmState.ACTIVE,
        )

    private fun relationship(realmId: String? = REALM_ID): Relationship =
        Relationship(
            relationshipId = "relationship-1",
            localIdentityPublicKey = alice.publicKeyEncoded,
            peerPublicKey = bob.publicKeyEncoded,
            peerDisplayName = "Bob",
            peerFingerprint = bob.fingerprint,
            realmId = realmId,
            state = RelationshipState.ACTIVE,
            createdAtEpochMillis = NOW,
            updatedAtEpochMillis = NOW,
            sourceInviteId = "invite-1",
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
        )

    private fun certificate(identity: LocalIdentity, capabilities: List<String>): MembershipCertificate =
        MembershipCertificate(
            realmId = REALM_ID,
            membershipId = "membership-${identity.identityId}",
            memberPublicKey = identity.publicKeyEncoded,
            issuedByPublicKey = alice.publicKeyEncoded,
            issuedAtEpochMillis = NOW,
            expiresAtEpochMillis = NOW + 60_000,
            capabilities = capabilities,
        )

    private fun snapshot(
        realm: Realm = this.realm,
        certificates: List<MembershipCertificate>,
    ): RealmSnapshot =
        RealmSnapshot(
            realms = listOf(realm),
            membershipCertificates = certificates,
            inviteEdges = emptyList(),
            pendingRequests = emptyList(),
        )

    private companion object {
        const val NOW = 1_700_000_000_000
        const val REALM_ID = "realm-1"
    }
}
