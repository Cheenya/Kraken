package com.disser.kraken.mesh

import com.disser.kraken.relationship.Relationship
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PeerRouteKind {
    NONE,
    DIRECT_BLE,
    DIRECT_LAN,
    ROUTED_MESH,
}

enum class BandwidthClass {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}

data class PeerRouteSnapshot(
    val relationshipId: String,
    val peerFingerprint: String,
    val kind: PeerRouteKind,
    val transportId: String? = null,
    val bandwidthClass: BandwidthClass,
    val hopCount: Int? = null,
    val lastSeenAtEpochMillis: Long? = null,
    val expiresAtEpochMillis: Long? = null,
    val evidence: List<DiscoveredPeerRouteEvidence> = emptyList(),
)

object PeerRouteAggregator {
    const val DEFAULT_ROUTE_TTL_MS = 30_000L

    fun snapshotFor(
        relationships: List<Relationship>,
        meshSnapshot: MeshServiceSnapshot,
        nowEpochMillis: Long = System.currentTimeMillis(),
        routeTtlMs: Long = DEFAULT_ROUTE_TTL_MS,
    ): List<PeerRouteSnapshot> {
        val freshEvidence = meshSnapshot.peerRouteEvidence
            .ifEmpty { meshSnapshot.transportDiagnostics.peerRouteEvidence }
            .filter { it.isFresh(nowEpochMillis, routeTtlMs) }

        return relationships.map { relationship ->
            val peerEvidence = freshEvidence.filter { it.fingerprint == relationship.peerFingerprint }
            val selected = selectBestEvidence(peerEvidence)
            PeerRouteSnapshot(
                relationshipId = relationship.relationshipId,
                peerFingerprint = relationship.peerFingerprint,
                kind = selected?.routeKind ?: PeerRouteKind.NONE,
                transportId = selected?.evidence?.transportId,
                bandwidthClass = selected?.bandwidthClass ?: BandwidthClass.NONE,
                hopCount = selected?.hopCount,
                lastSeenAtEpochMillis = peerEvidence.maxOfOrNull { it.observedAtEpochMillis },
                expiresAtEpochMillis = peerEvidence.maxOfOrNull { it.observedAtEpochMillis + routeTtlMs },
                evidence = peerEvidence,
            )
        }
    }

    fun routeFor(
        relationship: Relationship,
        meshSnapshot: MeshServiceSnapshot,
        nowEpochMillis: Long = System.currentTimeMillis(),
        routeTtlMs: Long = DEFAULT_ROUTE_TTL_MS,
    ): PeerRouteSnapshot =
        snapshotFor(listOf(relationship), meshSnapshot, nowEpochMillis, routeTtlMs).single()

    private fun selectBestEvidence(evidence: List<DiscoveredPeerRouteEvidence>): SelectedRouteEvidence? =
        evidence
            .mapNotNull { routeEvidence ->
                routeKindFor(routeEvidence.transportId)?.let { kind ->
                    SelectedRouteEvidence(
                        evidence = routeEvidence,
                        routeKind = kind,
                        bandwidthClass = bandwidthClassFor(kind),
                        hopCount = if (kind == PeerRouteKind.ROUTED_MESH) 2 else 1,
                    )
                }
            }
            .maxWithOrNull(
                compareBy<SelectedRouteEvidence> { priorityFor(it.routeKind) }
                    .thenBy { it.evidence.observedAtEpochMillis },
            )

    private fun DiscoveredPeerRouteEvidence.isFresh(nowEpochMillis: Long, routeTtlMs: Long): Boolean =
        observedAtEpochMillis <= nowEpochMillis + CLOCK_SKEW_TOLERANCE_MS &&
            nowEpochMillis - observedAtEpochMillis <= routeTtlMs

    private fun routeKindFor(transportId: String): PeerRouteKind? =
        when (transportId) {
            KrakenTransportCatalog.LAN_NSD_TCP.id -> PeerRouteKind.DIRECT_LAN
            KrakenTransportCatalog.WIFI_DIRECT.id -> PeerRouteKind.DIRECT_LAN
            KrakenTransportCatalog.BLE_GATT.id -> PeerRouteKind.DIRECT_BLE
            ROUTED_MESH_TRANSPORT_ID -> PeerRouteKind.ROUTED_MESH
            else -> null
        }

    private fun bandwidthClassFor(kind: PeerRouteKind): BandwidthClass =
        when (kind) {
            PeerRouteKind.DIRECT_LAN -> BandwidthClass.HIGH
            PeerRouteKind.DIRECT_BLE -> BandwidthClass.LOW
            PeerRouteKind.ROUTED_MESH -> BandwidthClass.LOW
            PeerRouteKind.NONE -> BandwidthClass.NONE
        }

    private fun priorityFor(kind: PeerRouteKind): Int =
        when (kind) {
            PeerRouteKind.DIRECT_LAN -> 3
            PeerRouteKind.DIRECT_BLE -> 2
            PeerRouteKind.ROUTED_MESH -> 1
            PeerRouteKind.NONE -> 0
        }

    private data class SelectedRouteEvidence(
        val evidence: DiscoveredPeerRouteEvidence,
        val routeKind: PeerRouteKind,
        val bandwidthClass: BandwidthClass,
        val hopCount: Int?,
    )

    private const val CLOCK_SKEW_TOLERANCE_MS = 1_000L
    private const val ROUTED_MESH_TRANSPORT_ID = "routed-mesh"
}

object PeerRouteFormatter {
    fun subtitle(
        route: PeerRouteSnapshot,
        meshState: MeshState,
        fallbackLastContactAtEpochMillis: Long? = null,
    ): String =
        when {
            meshState == MeshState.OFF -> "локальный контакт"
            route.kind == PeerRouteKind.DIRECT_LAN -> "Wi‑Fi/LAN напрямую"
            route.kind == PeerRouteKind.DIRECT_BLE -> "Bluetooth напрямую"
            route.kind == PeerRouteKind.ROUTED_MESH -> "через relay-прототип"
            route.lastSeenAtEpochMillis != null -> "последний контакт: ${compactTime(route.lastSeenAtEpochMillis)}"
            fallbackLastContactAtEpochMillis != null -> "последний контакт: ${compactTime(fallbackLastContactAtEpochMillis)}"
            else -> "нет маршрута"
        }

    private fun compactTime(epochMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale("ru", "RU")).format(Date(epochMillis))
}
