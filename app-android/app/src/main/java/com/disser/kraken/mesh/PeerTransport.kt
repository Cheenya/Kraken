package com.disser.kraken.mesh

@kotlinx.serialization.Serializable
data class DiscoveredPeer(
    val peerId: String,
    val fingerprint: String,
    val displayName: String? = null,
)

data class ReceivedPacket(
    val fromPeer: DiscoveredPeer,
    val packet: KrakenPacket,
    val receivedAtEpochMillis: Long,
)

data class TransportSendResult(
    val success: Boolean,
    val error: String? = null,
)

data class MeshRouteAttempt(
    val route: String,
    val success: Boolean,
    val error: String? = null,
    val atEpochMillis: Long,
)

data class DiscoveredPeerRouteEvidence(
    val fingerprint: String,
    val transportId: String,
    val observedAtEpochMillis: Long,
)

@kotlinx.serialization.Serializable
data class WifiDirectPeerEndpointDiagnostic(
    @kotlinx.serialization.SerialName("fingerprint_prefix")
    val fingerprintPrefix: String,
    @kotlinx.serialization.SerialName("device_address")
    val deviceAddress: String,
    @kotlinx.serialization.SerialName("device_name")
    val deviceName: String? = null,
    val host: String? = null,
    val port: Int? = null,
    @kotlinx.serialization.SerialName("binding_state")
    val bindingState: String,
    @kotlinx.serialization.SerialName("binding_source")
    val bindingSource: String,
    @kotlinx.serialization.SerialName("binding_reason")
    val bindingReason: String? = null,
)

@kotlinx.serialization.Serializable
data class WifiDirectTxtRecordDiagnostic(
    @kotlinx.serialization.SerialName("device_address")
    val deviceAddress: String,
    @kotlinx.serialization.SerialName("device_name")
    val deviceName: String? = null,
    @kotlinx.serialization.SerialName("fingerprint_prefix")
    val fingerprintPrefix: String? = null,
    val port: Int? = null,
    val keys: List<String> = emptyList(),
    val accepted: Boolean,
    val reason: String? = null,
)

@kotlinx.serialization.Serializable
data class WifiDirectVisibleDeviceDiagnostic(
    @kotlinx.serialization.SerialName("device_address")
    val deviceAddress: String,
    @kotlinx.serialization.SerialName("device_name")
    val deviceName: String? = null,
    val status: String? = null,
)

@kotlinx.serialization.Serializable
data class WifiDirectBoundEndpointDiagnostic(
    @kotlinx.serialization.SerialName("fingerprint_prefix")
    val fingerprintPrefix: String,
    @kotlinx.serialization.SerialName("device_address")
    val deviceAddress: String,
    @kotlinx.serialization.SerialName("device_name")
    val deviceName: String? = null,
    val host: String,
    val port: Int,
    @kotlinx.serialization.SerialName("binding_source")
    val bindingSource: String,
)

@kotlinx.serialization.Serializable
data class WifiDirectConnectAttemptDiagnostic(
    val attempt: Int,
    @kotlinx.serialization.SerialName("group_owner_intent")
    val groupOwnerIntent: Int,
    val result: String,
    @kotlinx.serialization.SerialName("failure_reason")
    val failureReason: Int? = null,
    @kotlinx.serialization.SerialName("failure_reason_name")
    val failureReasonName: String? = null,
    @kotlinx.serialization.SerialName("stop_peer_discovery_result")
    val stopPeerDiscoveryResult: String? = null,
    @kotlinx.serialization.SerialName("pre_connect_cancel_result")
    val preConnectCancelResult: String? = null,
)

fun TransportSendResult.rejectionReason(): MeshRejectionReason? {
    if (success) return null
    val normalized = error.orEmpty().lowercase()
    return when {
        normalized.contains("malformed") ||
            normalized.contains("invalid") ||
            normalized.contains("exceeds") ||
            normalized.contains("too-large") ||
            normalized.contains("frame") -> MeshRejectionReason.MALFORMED
        normalized.contains("expired") -> MeshRejectionReason.EXPIRED
        else -> MeshRejectionReason.UNKNOWN_PEER
    }
}

data class MeshTransportDiagnostics(
    val startedAtEpochMillis: Long? = null,
    val localPort: Int? = null,
    val localAddresses: List<String> = emptyList(),
    val registrationState: String = "not-started",
    val discoveryState: String = "not-started",
    val p2pVisibleDeviceCount: Int = 0,
    val p2pThisDeviceStatus: String? = null,
    val discoveryCycleCount: Int = 0,
    val p2pServiceFoundCount: Int = 0,
    val p2pTxtRecordCount: Int = 0,
    val p2pTxtRejectedCount: Int = 0,
    val p2pTxtBoundPeerCount: Int = 0,
    val p2pUnboundVisibleDeviceCount: Int = 0,
    val wifiDirectLastBindingError: String? = null,
    val multicastLockHeld: Boolean = false,
    val discoveredPeerCount: Int = 0,
    val manualPeerCount: Int = 0,
    val acceptedConnections: Int = 0,
    val inboundPackets: Int = 0,
    val malformedFramesDropped: Int = 0,
    val sendFailures: Int = 0,
    val lastError: String? = null,
    val transportModes: List<String> = emptyList(),
    val bleAdvertisingState: String = "not-started",
    val bleScanningState: String = "not-started",
    val bleGattServerState: String = "not-started",
    val bleConnectedPeerCount: Int = 0,
    val wifiDirectGroupFormed: Boolean? = null,
    val wifiDirectIsGroupOwner: Boolean? = null,
    val wifiDirectGroupRole: String = "unknown",
    val wifiDirectGroupOwnerAddress: String? = null,
    val wifiDirectLocalP2pAddress: String? = null,
    val wifiDirectServerBindAddress: String? = null,
    val wifiDirectLastSendHost: String? = null,
    val wifiDirectLastSendPort: Int? = null,
    val wifiDirectEndpointBindingState: String = "UNSEEN",
    val wifiDirectEndpointBindingReason: String? = null,
    val wifiDirectRelationshipPeerFingerprintPrefix: String? = null,
    val wifiDirectLastConnectDeviceAddress: String? = null,
    val wifiDirectLastConnectDeviceName: String? = null,
    val wifiDirectLastConnectGroupOwnerIntent: Int? = null,
    val wifiDirectLastConnectResult: String? = null,
    val wifiDirectLastConnectFailureReason: Int? = null,
    val wifiDirectConnectAttempts: List<WifiDirectConnectAttemptDiagnostic> = emptyList(),
    val wifiDirectDiscoveredPeers: List<WifiDirectPeerEndpointDiagnostic> = emptyList(),
    val wifiDirectVisibleDevices: List<WifiDirectVisibleDeviceDiagnostic> = emptyList(),
    val wifiDirectTxtRecords: List<WifiDirectTxtRecordDiagnostic> = emptyList(),
    val wifiDirectBoundEndpoints: List<WifiDirectBoundEndpointDiagnostic> = emptyList(),
    val p2pInterfaceAddresses: List<String> = emptyList(),
    val peerFingerprints: List<String> = emptyList(),
    val peerRouteEvidence: List<DiscoveredPeerRouteEvidence> = emptyList(),
    val recentRouteAttempts: List<MeshRouteAttempt> = emptyList(),
)

interface PeerTransport {
    val modeId: String
        get() = "transport"

    fun start()
    fun stop()
    fun observePeers(): List<DiscoveredPeer>
    fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult
    fun observePackets(): List<ReceivedPacket>
    fun diagnostics(): MeshTransportDiagnostics {
        val peers = observePeers()
        val observedAt = System.currentTimeMillis()
        return MeshTransportDiagnostics(
            discoveredPeerCount = peers.size,
            transportModes = listOf(modeId),
            peerFingerprints = peers.map { it.fingerprint }.distinct(),
            peerRouteEvidence = peers.map { peer ->
                DiscoveredPeerRouteEvidence(
                    fingerprint = peer.fingerprint,
                    transportId = modeId,
                    observedAtEpochMillis = observedAt,
                )
            },
        )
    }
}

interface ManualPeerTransport {
    fun addManualPeer(
        fingerprint: String,
        host: String,
        port: Int,
        displayName: String? = null,
    ): TransportSendResult
}

interface WifiDirectDebugControl {
    fun ensureDebugGroupOwner(): String
    fun addDebugPeer(
        fingerprint: String,
        deviceAddress: String,
        deviceName: String?,
        port: Int,
    ): String
}
