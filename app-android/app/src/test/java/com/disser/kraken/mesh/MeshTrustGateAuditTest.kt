package com.disser.kraken.mesh

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.CapacityState
import com.disser.kraken.realm.LocalRealmState
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmPolicy
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshTrustGateAuditTest {
    private val alice = identity("alice", "ALICE-FP")
    private val bob = identity("bob", "BOB-FP")

    @Test
    fun inboundPendingRelationshipRejected() {
        val result = MeshTrustGate.validateInbound(
            localIdentity = bob,
            relationships = listOf(relationship(bob, alice, RelationshipState.PENDING_HANDSHAKE)),
            packet = packet(sender = alice, recipient = bob),
            nowEpochMillis = 1_700_000_000_100,
        ).first

        assertEquals(MeshRejectionReason.PENDING_RELATIONSHIP, result.rejectionReason)
    }

    @Test
    fun inboundBlockedAndUnlinkedRejected() {
        listOf(RelationshipState.BLOCKED_BY_PEER, RelationshipState.UNLINKED).forEach { state ->
            val result = MeshTrustGate.validateInbound(
                localIdentity = bob,
                relationships = listOf(relationship(bob, alice, state)),
                packet = packet(sender = alice, recipient = bob),
                nowEpochMillis = 1_700_000_000_100,
            ).first

            assertEquals(MeshRejectionReason.BLOCKED_OR_UNLINKED, result.rejectionReason)
        }
    }

    @Test
    fun outboundRequiresActiveRelationship() {
        val result = MeshTrustGate.validateOutbound(
            localIdentity = alice,
            relationship = relationship(alice, bob, RelationshipState.PENDING_IMPORT),
            packet = packet(sender = alice, recipient = bob),
            nowEpochMillis = 1_700_000_000_100,
        )

        assertEquals(MeshRejectionReason.PENDING_RELATIONSHIP, result.rejectionReason)
    }

    @Test
    fun activeRelationshipAccepted() {
        val inbound = MeshTrustGate.validateInbound(
            localIdentity = bob,
            relationships = listOf(relationship(bob, alice, RelationshipState.ACTIVE)),
            packet = packet(sender = alice, recipient = bob),
            nowEpochMillis = 1_700_000_000_100,
        ).first

        assertTrue(inbound.accepted)
    }

    @Test
    fun inboundRealmPacketFromRemovedMemberRejected() {
        val result = MeshTrustGate.validateInbound(
            localIdentity = alice,
            relationships = listOf(relationship(alice, bob, RelationshipState.ACTIVE, realmId = REALM_ID)),
            packet = packet(sender = bob, recipient = alice),
            realmSnapshot = realmSnapshot(certificates = listOf(certificate(alice, listOf("send_direct")))),
            nowEpochMillis = 1_700_000_000_100,
        ).first

        assertEquals(MeshRejectionReason.REALM_MEMBERSHIP_BLOCKED, result.rejectionReason)
    }

    @Test
    fun outboundRealmPacketToRestrictedMemberRejected() {
        val relationship = relationship(alice, bob, RelationshipState.ACTIVE, realmId = REALM_ID)
        val result = MeshTrustGate.validateOutbound(
            localIdentity = alice,
            relationship = relationship,
            packet = packet(sender = alice, recipient = bob),
            realmSnapshot = realmSnapshot(
                certificates = listOf(
                    certificate(alice, listOf("send_direct")),
                    certificate(bob, listOf("restricted")),
                ),
            ),
            nowEpochMillis = 1_700_000_000_100,
        )

        assertEquals(MeshRejectionReason.REALM_MEMBERSHIP_BLOCKED, result.rejectionReason)
    }

    @Test
    fun trustAuditDocListsRequiredRejectionReasons() {
        val doc = File("../../docs/mesh-trust-gating-audit.md").readText()

        MeshRejectionReason.entries.forEach { reason ->
            assertTrue("Missing rejection reason in audit doc: $reason", doc.contains(reason.name))
        }
        assertTrue(doc.contains("LAN NSD discovery"))
        assertTrue(doc.contains("создать contact/chat/trust"))
        assertFalse(doc.contains("LAN peer = trusted contact"))
    }

    private fun identity(id: String, fingerprint: String): LocalIdentity =
        LocalIdentity(
            identityId = id,
            displayName = id.replaceFirstChar { it.uppercase() },
            publicKeyEncoded = "placeholder-pub:$id",
            privateKeyReference = "placeholder-private-ref:$id",
            fingerprint = fingerprint,
            createdAtEpochMillis = 1_700_000_000_000,
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
            offlineHandshakeRole = OfflineHandshakeRole.RESPONDER,
        )

    private fun packet(sender: LocalIdentity, recipient: LocalIdentity): KrakenPacket =
        KrakenPacket(
            packetId = "packet-1",
            packetType = KrakenPacketType.MESSAGE,
            senderFingerprint = sender.fingerprint,
            recipientFingerprint = recipient.fingerprint,
            relationshipId = "relationship-1",
            conversationId = "conversation-1",
            messageId = "message-1",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 4,
            payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
            payloadJson = """{"message_id":"message-1","body":"hello"}""",
        )

    private fun realmSnapshot(certificates: List<MembershipCertificate>): RealmSnapshot =
        RealmSnapshot(
            realms = listOf(
                Realm(
                    realmId = REALM_ID,
                    name = "OctoLab",
                    description = null,
                    createdByPublicKey = alice.publicKeyEncoded,
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
            issuedByPublicKey = alice.publicKeyEncoded,
            issuedAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            capabilities = capabilities,
        )

    private companion object {
        const val REALM_ID = "realm-1"
    }
}
