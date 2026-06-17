package com.disser.kraken.mesh

import java.util.Collections

class CompositePeerTransport(
    private val transports: List<PeerTransport>,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PeerTransport, ManualPeerTransport, WifiDirectDebugControl {
    private val routeAttempts = Collections.synchronizedList(mutableListOf<MeshRouteAttempt>())

    override val modeId: String = "multi-route"

    override fun start() {
        transports.forEach { transport ->
            runCatching { transport.start() }.onFailure { error ->
                recordRoute(transport.modeId, false, "start:${error.message ?: error::class.java.simpleName}")
            }
        }
    }

    override fun stop() {
        transports.forEach { transport -> runCatching { transport.stop() } }
        synchronized(routeAttempts) { routeAttempts.clear() }
    }

    override fun observePeers(): List<DiscoveredPeer> =
        transports
            .flatMap { transport -> runCatching { transport.observePeers() }.getOrDefault(emptyList()) }
            .fold(linkedMapOf<String, DiscoveredPeer>()) { acc, peer ->
                acc.putIfAbsent(peer.fingerprint, peer)
                acc
            }
            .values
            .toList()

    override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult {
        val candidates = transports.filter { transport ->
            runCatching { transport.observePeers().any { it.fingerprint == peer.fingerprint } }.getOrDefault(false)
        }.ifEmpty { transports }
        var lastError: String? = "peer-not-found"
        candidates.forEach { transport ->
            val routedPeer = runCatching {
                transport.observePeers().firstOrNull { it.fingerprint == peer.fingerprint } ?: peer
            }.getOrDefault(peer)
            val result = transport.send(routedPeer, packet)
            recordRoute(transport.modeId, result.success, result.error)
            if (result.success) return result
            lastError = "${transport.modeId}:${result.error ?: "send-failed"}"
        }
        return TransportSendResult(false, lastError)
    }

    override fun observePackets(): List<ReceivedPacket> =
        transports.flatMap { transport ->
            runCatching { transport.observePackets() }.getOrDefault(emptyList())
        }

    override fun diagnostics(): MeshTransportDiagnostics {
        val observedAt = clock()
        val transportSnapshots = transports.map { transport ->
            val peers = runCatching { transport.observePeers() }.getOrDefault(emptyList())
            val diagnostics = runCatching { transport.diagnostics() }.getOrDefault(MeshTransportDiagnostics())
            TransportDiagnosticsSnapshot(transport.modeId, peers, diagnostics)
        }
        val diagnostics = transportSnapshots.map { it.diagnostics }
        val peers = transportSnapshots
            .flatMap { it.peers }
            .fold(linkedMapOf<String, DiscoveredPeer>()) { acc, peer ->
                acc.putIfAbsent(peer.fingerprint, peer)
                acc
            }
            .values
            .toList()
        val routeEvidence = transportSnapshots.flatMap { snapshot ->
            snapshot.diagnostics.peerRouteEvidence.ifEmpty {
                snapshot.peers.map { peer ->
                    DiscoveredPeerRouteEvidence(
                        fingerprint = peer.fingerprint,
                        transportId = snapshot.transportId,
                        observedAtEpochMillis = observedAt,
                    )
                }
            }
        }
        val attempts = synchronized(routeAttempts) { routeAttempts.takeLast(MAX_ROUTE_ATTEMPTS) }
        return MeshTransportDiagnostics(
            startedAtEpochMillis = diagnostics.mapNotNull { it.startedAtEpochMillis }.minOrNull(),
            localPort = diagnostics.firstNotNullOfOrNull { it.localPort },
            localAddresses = diagnostics.flatMap { it.localAddresses }.distinct(),
            registrationState = diagnostics.joinToString(" | ") { "${it.transportModes.firstOrNull() ?: "transport"}:${it.registrationState}" },
            discoveryState = diagnostics.joinToString(" | ") { "${it.transportModes.firstOrNull() ?: "transport"}:${it.discoveryState}" },
            p2pVisibleDeviceCount = diagnostics.sumOf { it.p2pVisibleDeviceCount },
            p2pThisDeviceStatus = diagnostics.lastOrNull { it.p2pThisDeviceStatus != null }?.p2pThisDeviceStatus,
            discoveryCycleCount = diagnostics.sumOf { it.discoveryCycleCount },
            p2pServiceFoundCount = diagnostics.sumOf { it.p2pServiceFoundCount },
            p2pTxtRecordCount = diagnostics.sumOf { it.p2pTxtRecordCount },
            p2pTxtRejectedCount = diagnostics.sumOf { it.p2pTxtRejectedCount },
            p2pTxtBoundPeerCount = diagnostics.sumOf { it.p2pTxtBoundPeerCount },
            p2pUnboundVisibleDeviceCount = diagnostics.sumOf { it.p2pUnboundVisibleDeviceCount },
            wifiDirectLastBindingError = diagnostics.lastOrNull {
                it.wifiDirectLastBindingError != null
            }?.wifiDirectLastBindingError,
            multicastLockHeld = diagnostics.any { it.multicastLockHeld },
            discoveredPeerCount = peers.size,
            manualPeerCount = diagnostics.sumOf { it.manualPeerCount },
            acceptedConnections = diagnostics.sumOf { it.acceptedConnections },
            inboundPackets = diagnostics.sumOf { it.inboundPackets },
            malformedFramesDropped = diagnostics.sumOf { it.malformedFramesDropped },
            sendFailures = diagnostics.sumOf { it.sendFailures },
            lastError = diagnostics.lastOrNull { it.lastError != null }?.lastError,
            transportModes = transports.map { it.modeId },
            bleAdvertisingState = diagnostics.lastOrNull { it.bleAdvertisingState != "not-started" }?.bleAdvertisingState ?: "not-started",
            bleScanningState = diagnostics.lastOrNull { it.bleScanningState != "not-started" }?.bleScanningState ?: "not-started",
            bleGattServerState = diagnostics.lastOrNull { it.bleGattServerState != "not-started" }?.bleGattServerState ?: "not-started",
            bleConnectedPeerCount = diagnostics.sumOf { it.bleConnectedPeerCount },
            wifiDirectGroupFormed = diagnostics.lastOrNull { it.wifiDirectGroupFormed != null }?.wifiDirectGroupFormed,
            wifiDirectIsGroupOwner = diagnostics.lastOrNull { it.wifiDirectIsGroupOwner != null }?.wifiDirectIsGroupOwner,
            wifiDirectGroupRole = diagnostics.lastOrNull {
                it.wifiDirectGroupRole != "unknown"
            }?.wifiDirectGroupRole ?: "unknown",
            wifiDirectGroupOwnerAddress = diagnostics.lastOrNull { it.wifiDirectGroupOwnerAddress != null }?.wifiDirectGroupOwnerAddress,
            wifiDirectLocalP2pAddress = diagnostics.lastOrNull { it.wifiDirectLocalP2pAddress != null }?.wifiDirectLocalP2pAddress,
            wifiDirectServerBindAddress = diagnostics.lastOrNull { it.wifiDirectServerBindAddress != null }?.wifiDirectServerBindAddress,
            wifiDirectLastSendHost = diagnostics.lastOrNull { it.wifiDirectLastSendHost != null }?.wifiDirectLastSendHost,
            wifiDirectLastSendPort = diagnostics.lastOrNull { it.wifiDirectLastSendPort != null }?.wifiDirectLastSendPort,
            wifiDirectEndpointBindingState = diagnostics.lastOrNull {
                it.wifiDirectEndpointBindingState != "UNSEEN"
            }?.wifiDirectEndpointBindingState ?: diagnostics.lastOrNull()?.wifiDirectEndpointBindingState ?: "UNSEEN",
            wifiDirectEndpointBindingReason = diagnostics.lastOrNull {
                it.wifiDirectEndpointBindingReason != null
            }?.wifiDirectEndpointBindingReason,
            wifiDirectRelationshipPeerFingerprintPrefix = diagnostics.lastOrNull {
                it.wifiDirectRelationshipPeerFingerprintPrefix != null
            }?.wifiDirectRelationshipPeerFingerprintPrefix,
            wifiDirectLastConnectDeviceAddress = diagnostics.lastOrNull {
                it.wifiDirectLastConnectDeviceAddress != null
            }?.wifiDirectLastConnectDeviceAddress,
            wifiDirectLastConnectDeviceName = diagnostics.lastOrNull {
                it.wifiDirectLastConnectDeviceName != null
            }?.wifiDirectLastConnectDeviceName,
            wifiDirectLastConnectGroupOwnerIntent = diagnostics.lastOrNull {
                it.wifiDirectLastConnectGroupOwnerIntent != null
            }?.wifiDirectLastConnectGroupOwnerIntent,
            wifiDirectLastConnectResult = diagnostics.lastOrNull {
                it.wifiDirectLastConnectResult != null
            }?.wifiDirectLastConnectResult,
            wifiDirectLastConnectFailureReason = diagnostics.lastOrNull {
                it.wifiDirectLastConnectFailureReason != null
            }?.wifiDirectLastConnectFailureReason,
            wifiDirectConnectAttempts = diagnostics.flatMap { it.wifiDirectConnectAttempts }.takeLast(12),
            wifiDirectDiscoveredPeers = diagnostics.flatMap { it.wifiDirectDiscoveredPeers }.distinct(),
            wifiDirectVisibleDevices = diagnostics.flatMap { it.wifiDirectVisibleDevices }.distinct(),
            wifiDirectTxtRecords = diagnostics.flatMap { it.wifiDirectTxtRecords }.takeLast(12),
            wifiDirectBoundEndpoints = diagnostics.flatMap { it.wifiDirectBoundEndpoints }.distinct(),
            p2pInterfaceAddresses = diagnostics.flatMap { it.p2pInterfaceAddresses }.distinct(),
            peerFingerprints = peers.map { it.fingerprint }.distinct(),
            peerRouteEvidence = routeEvidence,
            recentRouteAttempts = attempts,
        )
    }

    override fun addManualPeer(
        fingerprint: String,
        host: String,
        port: Int,
        displayName: String?,
    ): TransportSendResult {
        val manual = transports.firstOrNull { it is ManualPeerTransport } as? ManualPeerTransport
            ?: return TransportSendResult(false, "manual-peer-unavailable")
        return manual.addManualPeer(fingerprint, host, port, displayName)
    }

    override fun ensureDebugGroupOwner(): String {
        val wifiDirect = transports.firstOrNull { it is WifiDirectDebugControl } as? WifiDirectDebugControl
            ?: return "wifi-direct-control-unavailable"
        return wifiDirect.ensureDebugGroupOwner()
    }

    override fun addDebugPeer(
        fingerprint: String,
        deviceAddress: String,
        deviceName: String?,
        port: Int,
    ): String {
        val wifiDirect = transports.firstOrNull { it is WifiDirectDebugControl } as? WifiDirectDebugControl
            ?: return "wifi-direct-control-unavailable"
        return wifiDirect.addDebugPeer(fingerprint, deviceAddress, deviceName, port)
    }

    private fun recordRoute(route: String, success: Boolean, error: String?) {
        synchronized(routeAttempts) {
            routeAttempts += MeshRouteAttempt(route, success, error, clock())
            if (routeAttempts.size > MAX_ROUTE_ATTEMPTS) {
                routeAttempts.removeAt(0)
            }
        }
    }

    private companion object {
        const val MAX_ROUTE_ATTEMPTS = 12
    }

    private data class TransportDiagnosticsSnapshot(
        val transportId: String,
        val peers: List<DiscoveredPeer>,
        val diagnostics: MeshTransportDiagnostics,
    )
}
