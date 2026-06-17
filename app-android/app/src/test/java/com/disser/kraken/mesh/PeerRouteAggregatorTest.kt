package com.disser.kraken.mesh

import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PeerRouteAggregatorTest {
    @Test
    fun bleOnlyEvidenceMapsToDirectBle() {
        val route = routeFor(
            evidence = listOf(evidence(KrakenTransportCatalog.BLE_GATT.id, observedAt = 10_000)),
            now = 11_000,
        )

        assertEquals(PeerRouteKind.DIRECT_BLE, route.kind)
        assertEquals(KrakenTransportCatalog.BLE_GATT.id, route.transportId)
        assertEquals(BandwidthClass.LOW, route.bandwidthClass)
    }

    @Test
    fun lanOnlyEvidenceMapsToDirectLan() {
        val route = routeFor(
            evidence = listOf(evidence(KrakenTransportCatalog.LAN_NSD_TCP.id, observedAt = 10_000)),
            now = 11_000,
        )

        assertEquals(PeerRouteKind.DIRECT_LAN, route.kind)
        assertEquals(KrakenTransportCatalog.LAN_NSD_TCP.id, route.transportId)
        assertEquals(BandwidthClass.HIGH, route.bandwidthClass)
    }

    @Test
    fun wifiDirectEvidenceMapsToDirectLanWithoutLosingTransportId() {
        val route = routeFor(
            evidence = listOf(evidence(KrakenTransportCatalog.WIFI_DIRECT.id, observedAt = 10_000)),
            now = 11_000,
        )

        assertEquals(PeerRouteKind.DIRECT_LAN, route.kind)
        assertEquals(KrakenTransportCatalog.WIFI_DIRECT.id, route.transportId)
        assertEquals(BandwidthClass.HIGH, route.bandwidthClass)
    }

    @Test
    fun directLanWinsOverDirectBleWhenBothAreFresh() {
        val route = routeFor(
            evidence = listOf(
                evidence(KrakenTransportCatalog.BLE_GATT.id, observedAt = 12_000),
                evidence(KrakenTransportCatalog.LAN_NSD_TCP.id, observedAt = 10_000),
            ),
            now = 13_000,
        )

        assertEquals(PeerRouteKind.DIRECT_LAN, route.kind)
        assertEquals(KrakenTransportCatalog.LAN_NSD_TCP.id, route.transportId)
    }

    @Test
    fun staleEvidenceMapsToNoRoute() {
        val route = routeFor(
            evidence = listOf(evidence(KrakenTransportCatalog.BLE_GATT.id, observedAt = 10_000)),
            now = 45_001,
        )

        assertEquals(PeerRouteKind.NONE, route.kind)
        assertEquals(BandwidthClass.NONE, route.bandwidthClass)
    }

    @Test
    fun directBleSubtitleDoesNotClaimGenericMeshAvailability() {
        val route = routeFor(
            evidence = listOf(evidence(KrakenTransportCatalog.BLE_GATT.id, observedAt = 10_000)),
            now = 11_000,
        )

        val label = PeerRouteFormatter.subtitle(route, MeshState.PEER_FOUND)

        assertEquals("Bluetooth напрямую", label)
        assertFalse(label.contains("mesh", ignoreCase = true))
    }

    private fun routeFor(
        evidence: List<DiscoveredPeerRouteEvidence>,
        now: Long,
    ): PeerRouteSnapshot =
        PeerRouteAggregator.routeFor(
            relationship = relationship(),
            meshSnapshot = MeshServiceSnapshot(
                state = MeshState.PEER_FOUND,
                peerRouteEvidence = evidence,
            ),
            nowEpochMillis = now,
        )

    private fun evidence(
        transportId: String,
        observedAt: Long,
        fingerprint: String = PEER_FINGERPRINT,
    ): DiscoveredPeerRouteEvidence =
        DiscoveredPeerRouteEvidence(
            fingerprint = fingerprint,
            transportId = transportId,
            observedAtEpochMillis = observedAt,
        )

    private fun relationship(): Relationship =
        Relationship(
            relationshipId = "relationship-1",
            localIdentityPublicKey = "local-public-key",
            peerPublicKey = "peer-public-key",
            peerDisplayName = "Peer",
            peerFingerprint = PEER_FINGERPRINT,
            realmId = null,
            state = RelationshipState.ACTIVE,
            createdAtEpochMillis = 1_000,
            updatedAtEpochMillis = 1_000,
            sourceInviteId = null,
        )

    private companion object {
        const val PEER_FINGERPRINT = "PEER-FINGERPRINT"
    }
}
