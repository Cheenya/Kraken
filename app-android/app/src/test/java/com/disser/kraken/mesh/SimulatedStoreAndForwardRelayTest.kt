package com.disser.kraken.mesh

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.CapacityState
import com.disser.kraken.realm.LocalRealmState
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmPolicy
import com.disser.kraken.realm.RealmRelayBlockReason
import com.disser.kraken.realm.RealmSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedStoreAndForwardRelayTest {
    private val alice = DiscoveredPeer("peer-a", "ALICE-FP", "Alice")
    private val bob = DiscoveredPeer("peer-b", "BOB-FP", "Bob")
    private val relay = DiscoveredPeer("peer-c", "RELAY-FP", "Relay")

    @Test
    fun relayDisabledBlocksTransitForwarding() {
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val relayTransport = InMemoryTwoNodeTransport(relay, bus) { 1_700_000_000_000 }
        relayTransport.start()

        val result = SimulatedStoreAndForwardRelay(prototypeRelayEnabled = false) { 1_700_000_000_100 }
            .relay(packet(), bob, relayTransport)

        assertEquals(RelaySimulationStatus.RELAY_DISABLED, result.status)
        assertNull(result.packet)
    }

    @Test
    fun enabledRelayForwardsPacketAndDecrementsTtl() {
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val bobTransport = InMemoryTwoNodeTransport(bob, bus) { 1_700_000_000_000 }
        val relayTransport = InMemoryTwoNodeTransport(relay, bus) { 1_700_000_000_000 }
        bobTransport.start()
        relayTransport.start()

        val result = SimulatedStoreAndForwardRelay(prototypeRelayEnabled = true) { 1_700_000_000_100 }
            .relay(packet(ttlHops = 3), bob, relayTransport)

        assertEquals(RelaySimulationStatus.FORWARDED, result.status)
        assertEquals(2, result.packet?.ttlHops)
        val received = bobTransport.observePackets().single()
        assertEquals("ALICE-FP", received.packet.senderFingerprint)
        assertEquals("BOB-FP", received.packet.recipientFingerprint)
        assertEquals(2, received.packet.ttlHops)
    }

    @Test
    fun realmPolicyMustAllowRelayBeforeForwarding() {
        val localIdentity = identity("alice")
        val relayIdentity = identity("relay")
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val bobTransport = InMemoryTwoNodeTransport(bob, bus) { 1_700_000_000_000 }
        val relayTransport = InMemoryTwoNodeTransport(relay, bus) { 1_700_000_000_000 }
        bobTransport.start()
        relayTransport.start()
        val relayService = SimulatedStoreAndForwardRelay(prototypeRelayEnabled = true) { 1_700_000_000_100 }

        val blocked = relayService.relayInRealm(
            localIdentity = localIdentity,
            realmId = REALM_ID,
            relayPeerPublicKey = relayIdentity.publicKeyEncoded,
            realmSnapshot = realmSnapshot(
                certificates = listOf(
                    certificate(localIdentity, listOf("send_direct", "relay_basic")),
                    certificate(relayIdentity, listOf("send_direct")),
                ),
            ),
            packet = packet(ttlHops = 3),
            nextPeer = bob,
            transport = relayTransport,
        )

        assertEquals(RelaySimulationStatus.RELAY_POLICY_BLOCKED, blocked.status)
        assertEquals(RealmRelayBlockReason.RELAY_BASIC_MISSING, blocked.relayBlockReason)
        assertTrue(bobTransport.observePackets().isEmpty())

        val forwarded = relayService.relayInRealm(
            localIdentity = localIdentity,
            realmId = REALM_ID,
            relayPeerPublicKey = relayIdentity.publicKeyEncoded,
            realmSnapshot = realmSnapshot(
                certificates = listOf(
                    certificate(localIdentity, listOf("send_direct", "relay_basic")),
                    certificate(relayIdentity, listOf("relay_basic")),
                ),
            ),
            packet = packet(ttlHops = 3),
            nextPeer = bob,
            transport = relayTransport,
        )

        assertEquals(RelaySimulationStatus.FORWARDED, forwarded.status)
        assertEquals(2, bobTransport.observePackets().single().packet.ttlHops)
    }

    @Test
    fun duplicateExpiredAndTtlExhaustedPacketsAreDropped() {
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val relayTransport = InMemoryTwoNodeTransport(relay, bus) { 1_700_000_000_000 }
        relayTransport.start()
        val relayService = SimulatedStoreAndForwardRelay(prototypeRelayEnabled = true) { 1_700_000_000_100 }

        assertEquals(
            MeshRejectionReason.DUPLICATE,
            relayService.relay(packet(), bob, relayTransport, alreadySeen = true).rejectionReason,
        )
        assertEquals(
            MeshRejectionReason.EXPIRED,
            relayService.relay(packet(expiresAtEpochMillis = 1_700_000_000_000), bob, relayTransport).rejectionReason,
        )
        assertEquals(
            MeshRejectionReason.TTL_EXHAUSTED,
            relayService.relay(packet(ttlHops = 0), bob, relayTransport).rejectionReason,
        )
    }

    @Test
    fun receiptCanRouteBackThroughSimulation() {
        val bus = InMemoryTwoNodeTransport.SharedBus()
        val aliceTransport = InMemoryTwoNodeTransport(alice, bus) { 1_700_000_000_000 }
        val relayTransport = InMemoryTwoNodeTransport(relay, bus) { 1_700_000_000_000 }
        aliceTransport.start()
        relayTransport.start()
        val receipt = packet(
            packetType = KrakenPacketType.RECEIPT,
            senderFingerprint = bob.fingerprint,
            recipientFingerprint = alice.fingerprint,
            payloadType = PacketPayloadType.RECEIPT_JSON,
            payloadJson = """{"packet_id":"packet-msg","message_id":"message-1"}""",
        )

        val result = SimulatedStoreAndForwardRelay(prototypeRelayEnabled = true) { 1_700_000_000_100 }
            .relay(receipt, alice, relayTransport)

        assertEquals(RelaySimulationStatus.FORWARDED, result.status)
        assertTrue(aliceTransport.observePackets().single().packet.packetType == KrakenPacketType.RECEIPT)
    }

    private fun packet(
        ttlHops: Int = 4,
        expiresAtEpochMillis: Long = 1_700_000_060_000,
        packetType: KrakenPacketType = KrakenPacketType.MESSAGE,
        senderFingerprint: String = alice.fingerprint,
        recipientFingerprint: String = bob.fingerprint,
        payloadType: PacketPayloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
        payloadJson: String = """{"message_id":"message-1","body":"hello"}""",
    ): KrakenPacket =
        KrakenPacket(
            packetId = "packet-${packetType.name}",
            packetType = packetType,
            senderFingerprint = senderFingerprint,
            recipientFingerprint = recipientFingerprint,
            relationshipId = "relationship-1",
            conversationId = "conversation-1",
            messageId = "message-1",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = expiresAtEpochMillis,
            ttlHops = ttlHops,
            payloadType = payloadType,
            payloadJson = payloadJson,
        )

    private fun identity(id: String): LocalIdentity =
        LocalIdentity(
            identityId = id,
            displayName = id.replaceFirstChar { it.uppercase() },
            publicKeyEncoded = "placeholder-pub:$id",
            privateKeyReference = "placeholder-private-ref:$id",
            fingerprint = "$id-fp",
            createdAtEpochMillis = 1_700_000_000_000,
        )

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
}
