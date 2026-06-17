package com.disser.kraken.realm

import com.disser.kraken.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealmRelayPolicyTest {
    private val alice = identity("alice", "ALICE-FP")
    private val relay = identity("relay", "RELAY-FP")
    private val realm = realm()

    @Test
    fun realmMemberWithRelayCapabilityCanBeUsedWithoutRelationship() {
        val decision = RealmRelayPolicy.canUseRelayPeer(
            localIdentity = alice,
            realmId = REALM_ID,
            relayPeerPublicKey = relay.publicKeyEncoded,
            realmSnapshot = snapshot(
                certificates = listOf(
                    certificate(alice, listOf("send_direct", "relay_basic")),
                    certificate(relay, listOf("relay_basic")),
                ),
            ),
            nowEpochMillis = NOW,
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun directOnlyRealmDoesNotAllowRelayPeer() {
        val decision = RealmRelayPolicy.canUseRelayPeer(
            localIdentity = alice,
            realmId = REALM_ID,
            relayPeerPublicKey = relay.publicKeyEncoded,
            realmSnapshot = snapshot(
                realm = realm.copy(localState = LocalRealmState.ONLY_DIRECT),
                certificates = listOf(
                    certificate(alice, listOf("relay_basic")),
                    certificate(relay, listOf("relay_basic")),
                ),
            ),
            nowEpochMillis = NOW,
        )

        assertFalse(decision.allowed)
        assertEquals(RealmRelayBlockReason.REALM_NOT_ACTIVE, decision.blockReason)
    }

    @Test
    fun realmPolicyCanDisableTransit() {
        val decision = RealmRelayPolicy.canUseRelayPeer(
            localIdentity = alice,
            realmId = REALM_ID,
            relayPeerPublicKey = relay.publicKeyEncoded,
            realmSnapshot = snapshot(
                realm = realm.copy(policy = RealmPolicy(allowTransit = false)),
                certificates = listOf(
                    certificate(alice, listOf("relay_basic")),
                    certificate(relay, listOf("relay_basic")),
                ),
            ),
            nowEpochMillis = NOW,
        )

        assertFalse(decision.allowed)
        assertEquals(RealmRelayBlockReason.REALM_TRANSIT_DISABLED, decision.blockReason)
    }

    @Test
    fun relayPeerMustBelongToSameRealm() {
        val decision = RealmRelayPolicy.canUseRelayPeer(
            localIdentity = alice,
            realmId = REALM_ID,
            relayPeerPublicKey = relay.publicKeyEncoded,
            realmSnapshot = snapshot(certificates = listOf(certificate(alice, listOf("relay_basic")))),
            nowEpochMillis = NOW,
        )

        assertFalse(decision.allowed)
        assertEquals(RealmRelayBlockReason.RELAY_MEMBERSHIP_MISSING, decision.blockReason)
    }

    @Test
    fun relayPeerMustHaveRelayCapability() {
        val decision = RealmRelayPolicy.canUseRelayPeer(
            localIdentity = alice,
            realmId = REALM_ID,
            relayPeerPublicKey = relay.publicKeyEncoded,
            realmSnapshot = snapshot(
                certificates = listOf(
                    certificate(alice, listOf("relay_basic")),
                    certificate(relay, listOf("send_direct")),
                ),
            ),
            nowEpochMillis = NOW,
        )

        assertFalse(decision.allowed)
        assertEquals(RealmRelayBlockReason.RELAY_BASIC_MISSING, decision.blockReason)
    }

    @Test
    fun relayPolicyDoesNotGrantDirectMessageRights() {
        val directDecision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = alice,
            relationship = RealmCommunicationPolicyTestFixtures.relationship(
                localIdentity = alice,
                peerIdentity = relay,
                realmId = REALM_ID,
            ),
            realmSnapshot = snapshot(
                certificates = listOf(
                    certificate(alice, listOf("relay_basic")),
                    certificate(relay, listOf("relay_basic")),
                ),
            ),
            nowEpochMillis = NOW,
        )

        assertFalse(directDecision.allowed)
        assertEquals(RealmCommunicationBlockReason.LOCAL_SEND_DIRECT_MISSING, directDecision.blockReason)
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
