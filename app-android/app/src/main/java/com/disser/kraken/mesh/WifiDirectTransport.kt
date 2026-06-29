package com.disser.kraken.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Looper
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WifiDirectTransport(
    context: Context,
    private val localPeer: DiscoveredPeer,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PeerTransport, WifiDirectDebugControl {
    override val modeId: String = KrakenTransportCatalog.WIFI_DIRECT.id

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val peers = Collections.synchronizedMap(linkedMapOf<String, WifiDirectPeer>())
    private val visibleDevices = Collections.synchronizedList(mutableListOf<WifiDirectVisibleDevice>())
    private val txtRecordDiagnostics = Collections.synchronizedList(mutableListOf<WifiDirectTxtRecordDiagnostic>())
    private val connectAttemptDiagnostics =
        Collections.synchronizedList(mutableListOf<WifiDirectConnectAttemptDiagnostic>())
    private val incoming = Collections.synchronizedList(mutableListOf<ReceivedPacket>())
    private val running = AtomicBoolean(false)
    private val p2pNegotiationInProgress = AtomicBoolean(false)
    @Volatile
    private var diagnostics = MeshTransportDiagnostics(transportModes = listOf(modeId))
    @Volatile
    private var connectionInfo: WifiP2pInfo? = null
    private var channel: WifiP2pManager.Channel? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null
    private var serverSocket: WifiDirectServerSocket? = null
    private var serverThread: Thread? = null
    private var rediscoveryThread: Thread? = null
    private var receiver: BroadcastReceiver? = null

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        updateDiagnostics {
            it.copy(
                startedAtEpochMillis = clock(),
                transportModes = listOf(modeId),
                registrationState = "starting",
                discoveryState = "starting",
            )
        }
        if (!WifiDirectPermissions.hasRuntimePermissions(appContext)) {
            updateDiagnostics {
                it.copy(
                    registrationState = "permission-missing",
                    discoveryState = "permission-missing",
                    lastError = "wifi-direct-permission-missing",
                )
            }
            running.set(false)
            return
        }
        val p2pManager = manager
        if (p2pManager == null) {
            updateDiagnostics {
                it.copy(
                    registrationState = "unsupported",
                    discoveryState = "unsupported",
                    lastError = "wifi-direct-manager-unavailable",
                )
            }
            running.set(false)
            return
        }
        val p2pChannel = p2pManager.initialize(appContext, Looper.getMainLooper(), null)
        if (p2pChannel == null) {
            updateDiagnostics {
                it.copy(
                    registrationState = "channel-unavailable",
                    discoveryState = "channel-unavailable",
                    lastError = "wifi-direct-channel-unavailable",
                )
            }
            running.set(false)
            return
        }
        channel = p2pChannel
        val socket = newWifiDirectServerSocket()
        serverSocket = socket
        updateDiagnostics {
            val p2pAddresses = p2pIpv4Addresses()
            it.copy(
                wifiDirectServerBindAddress = socket.bindAddress.hostAddress,
                localPort = socket.port,
                localAddresses = localIpv4Addresses(),
                p2pInterfaceAddresses = p2pAddresses,
                wifiDirectLocalP2pAddress = p2pAddresses.firstOrNull(),
            )
        }
        serverThread = Thread({ acceptLoop(socket) }, "kraken-wifi-direct-accept").also { it.start() }
        registerReceiver()
        resetStaleP2pNegotiation(p2pManager, p2pChannel, "start")
        publishLocalService(p2pManager, p2pChannel, socket.port)
        discoverServices(p2pManager, p2pChannel)
        startRediscoveryLoop()
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        running.set(false)
        val p2pManager = manager
        val p2pChannel = channel
        if (p2pManager != null && p2pChannel != null) {
            serviceRequest?.let { request ->
                runCatching { p2pManager.removeServiceRequest(p2pChannel, request, noopActionListener()) }
            }
            runCatching { p2pManager.stopPeerDiscovery(p2pChannel, noopActionListener()) }
            runCatching { p2pManager.clearServiceRequests(p2pChannel, noopActionListener()) }
            runCatching { p2pManager.clearLocalServices(p2pChannel, noopActionListener()) }
        }
        runCatching { receiver?.let(appContext::unregisterReceiver) }
        runCatching { rediscoveryThread?.interrupt() }
        runCatching { serverSocket?.close() }
        synchronized(peers) { peers.clear() }
        synchronized(connectAttemptDiagnostics) { connectAttemptDiagnostics.clear() }
        incoming.clear()
        serviceRequest = null
        channel = null
        receiver = null
        rediscoveryThread = null
        serverSocket = null
        serverThread = null
        connectionInfo = null
        p2pNegotiationInProgress.set(false)
        updateDiagnostics {
            it.copy(
                registrationState = "stopped",
                discoveryState = "stopped",
                discoveredPeerCount = 0,
            )
        }
    }

    override fun observePeers(): List<DiscoveredPeer> {
        val observed = synchronized(peers) { peers.values.map { it.peer }.distinctBy { it.fingerprint } }
        updateDiagnostics { it.copy(discoveredPeerCount = observed.size) }
        return observed
    }

    override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult {
        val directPeer = directPeerForRelationshipPeer(peer)
            ?: run {
                val (state, reason) = currentEndpointBindingFailure(
                    peer = peer,
                    defaultState = "UNSEEN",
                    defaultReason = missingPeerReason(peer),
                )
                recordEndpointBindingFailure(peer, state, reason)
                recordSendFailure("wifi-direct-peer-not-found:${peer.fingerprint}")
                return TransportSendResult(false, "wifi-direct-peer-not-found:$reason")
            }

        val sendPeer = resolveSendPeer(directPeer, allowCachedHost = true)
            ?: run {
                val reason = endpointUnavailableReason(directPeer)
                val failedBinding = WifiDirectEndpointResolver.connectFailed(
                    peer = peer,
                    deviceAddress = directPeer.deviceAddress,
                    deviceName = directPeer.deviceName,
                    port = directPeer.port,
                    reason = reason,
                    source = directPeer.bindingSource,
                )
                upsertPeer(
                    directPeer.copy(
                        host = null,
                        bindingSource = failedBinding.source,
                        bindingState = failedBinding.state.name,
                        bindingReason = failedBinding.reason,
                    ),
                )
                recordEndpointBindingFailure(
                    peer = peer,
                    state = failedBinding.state.name,
                    reason = reason,
                )
                recordSendFailure("wifi-direct-endpoint-unavailable:${directPeer.deviceAddress}")
                return TransportSendResult(false, "wifi-direct-endpoint-unavailable:$reason")
            }
        recordEndpointBinding(peer = peer, state = "BOUND", reason = null, endpoint = sendPeer)
        var result = sendToPeer(sendPeer, packet)
        if (result.success) {
            return result
        }

        repeat(SEND_ENDPOINT_RETRY_COUNT) { attemptIndex ->
            Thread.sleep(SEND_ENDPOINT_RETRY_DELAY_MS)
            val retryPeer = resolveSendPeer(directPeer, allowCachedHost = false) ?: sendPeer
            updateDiagnostics {
                it.copy(discoveryState = "send-retry:${attemptIndex + 1}:${retryPeer.host?.hostAddress}:${retryPeer.port}")
            }
            result = sendToPeer(retryPeer, packet)
            if (result.success) {
                return result
            }
        }
        return result
    }

    private fun directPeerForRelationshipPeer(peer: DiscoveredPeer): WifiDirectPeer? {
        val existing = synchronized(peers) {
            peers.values.firstOrNull { it.peer.fingerprint == peer.fingerprint }
        } ?: return fallbackPeerForRelationshipPeer(peer)
        if (existing.host != null) return existing

        val info = connectionInfo ?: requestConnectionInfoBlocking()
        if (info?.groupFormed == true) return existing

        if (existing.bindingSource == "single-visible-device-connect") {
            val visibleDevice = awaitVisibleDeviceForRelationshipPeer(peer, "stale-single-visible-reresolve")
                ?: return null.also {
                    synchronized(peers) {
                        peers.entries.removeAll { entry -> entry.value.peer.fingerprint == peer.fingerprint }
                    }
                    recordEndpointBindingFailure(
                        peer = peer,
                        state = "STALE",
                        reason = "single-visible-device-stale-no-current-target-visible:${existing.deviceAddress}",
                    )
                }
            if (normalizeDeviceAddress(visibleDevice.deviceAddress) != normalizeDeviceAddress(existing.deviceAddress)) {
                synchronized(peers) {
                    peers.entries.removeAll { it.value.peer.fingerprint == peer.fingerprint }
                }
            }
            return fallbackPeerForRelationshipPeer(peer)
        }

        return existing
    }

    override fun observePackets(): List<ReceivedPacket> {
        synchronized(incoming) {
            val packets = incoming.toList()
            incoming.clear()
            return packets
        }
    }

    @SuppressLint("MissingPermission")
    override fun ensureDebugGroupOwner(): String {
        val p2pManager = manager ?: return "wifi-direct-manager-unavailable"
        val p2pChannel = channel ?: return "wifi-direct-channel-unavailable"
        requestConnectionInfoBlocking()?.let { info ->
            if (info.groupFormed && info.isGroupOwner) {
                updateDiagnostics {
                    it.copy(
                        discoveryState = "debug-group-owner-already-active",
                        wifiDirectGroupFormed = true,
                        wifiDirectIsGroupOwner = true,
                        wifiDirectGroupRole = "owner",
                        wifiDirectGroupOwnerAddress = info.groupOwnerAddress?.hostAddress,
                        lastError = null,
                    )
                }
                republishLocalServiceAfterGroupOwner(p2pManager, p2pChannel, "already-active")
                return "already-owner"
            }
        }

        val latch = CountDownLatch(1)
        var failureReason: Int? = null
        updateDiagnostics {
            it.copy(
                discoveryState = "debug-create-group-requested",
                lastError = null,
            )
        }
        p2pManager.createGroup(
            p2pChannel,
            actionListener(
                onSuccess = {
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "debug-create-group-accepted",
                            lastError = null,
                        )
                    }
                    refreshConnectionInfo()
                    latch.countDown()
                },
                onFailure = { reason ->
                    failureReason = reason
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "debug-create-group-failed:$reason",
                            lastError = "wifi-direct-create-group-failed:$reason",
                        )
                    }
                    latch.countDown()
                },
            ),
        )
        if (!latch.await(CREATE_GROUP_ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            updateDiagnostics {
                it.copy(
                    discoveryState = "debug-create-group-timeout",
                    lastError = "wifi-direct-create-group-timeout",
                )
            }
            return "timeout"
        }
        failureReason?.let { return "failed:$it" }
        val deadline = clock() + CREATE_GROUP_FORMATION_WAIT_MS
        while (clock() <= deadline && running.get()) {
            val info = requestConnectionInfoBlocking()
            if (info?.groupFormed == true && info.isGroupOwner) {
                republishLocalServiceAfterGroupOwner(p2pManager, p2pChannel, "formed")
                restartDiscoveryCycle("debug-group-owner")
                return "owner"
            }
            Thread.sleep(CONNECTION_INFO_POLL_MS)
        }
        return "accepted-but-owner-not-formed"
    }

    override fun addDebugPeer(
        fingerprint: String,
        deviceAddress: String,
        deviceName: String?,
        port: Int,
    ): String {
        if (fingerprint.isBlank()) return "missing-fingerprint"
        val normalizedAddress = normalizeDeviceAddress(deviceAddress) ?: return "invalid-device-address"
        if (port !in 1..65535) return "invalid-port"
        val binding = WifiDirectEndpointResolver.debugHint(
            fingerprint = fingerprint,
            deviceAddress = normalizedAddress,
            deviceName = deviceName,
            port = port,
        )
        val peer = WifiDirectPeer(
            peer = DiscoveredPeer(
                peerId = "wifi-direct-debug-$normalizedAddress-$fingerprint",
                fingerprint = fingerprint,
                displayName = binding.deviceName,
            ),
            deviceAddress = binding.deviceAddress ?: normalizedAddress,
            deviceName = binding.deviceName,
            port = binding.port ?: port,
            host = null,
            bindingSource = binding.source,
            bindingState = binding.state.name,
            bindingReason = binding.reason,
        )
        upsertPeer(peer)
        recordEndpointBinding(
            peer = peer.peer,
            state = binding.state.name,
            reason = binding.reason,
            endpoint = null,
        )
        updateDiagnostics {
            it.copy(
                discoveryState = "debug-wifi-direct-peer-added:${peer.deviceName ?: normalizedAddress}",
                discoveredPeerCount = peerCount(),
                wifiDirectDiscoveredPeers = peerEndpointDiagnostics(),
                lastError = null,
            )
        }
        return "added:$normalizedAddress:$port"
    }

    @SuppressLint("MissingPermission")
    private fun republishLocalServiceAfterGroupOwner(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        reason: String,
    ) {
        val port = serverSocket?.port ?: DEFAULT_WIFI_DIRECT_PORT
        updateDiagnostics {
            it.copy(discoveryState = "debug-group-owner-republish-service:$reason:$port")
        }
        publishLocalService(p2pManager, p2pChannel, port)
    }

    override fun diagnostics(): MeshTransportDiagnostics {
        val observed = observePeers()
        val p2pAddresses = p2pIpv4Addresses()
        return diagnostics.copy(
            transportModes = listOf(modeId),
            discoveredPeerCount = observed.size,
            localPort = serverSocket?.port,
            localAddresses = diagnostics.localAddresses.ifEmpty { localIpv4Addresses() },
            p2pInterfaceAddresses = p2pAddresses,
            wifiDirectLocalP2pAddress = p2pAddresses.firstOrNull(),
            wifiDirectDiscoveredPeers = peerEndpointDiagnostics(),
            wifiDirectVisibleDevices = visibleDeviceDiagnostics(),
            wifiDirectTxtRecords = synchronized(txtRecordDiagnostics) { txtRecordDiagnostics.toList() },
            wifiDirectBoundEndpoints = boundEndpointDiagnostics(),
            peerRouteEvidence = observed.map { peer ->
                DiscoveredPeerRouteEvidence(
                    fingerprint = peer.fingerprint,
                    transportId = modeId,
                    observedAtEpochMillis = clock(),
                )
            },
            peerFingerprints = observed.map { it.fingerprint }.distinct(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun publishLocalService(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        port: Int,
    ) {
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            serviceInstanceName(),
            SERVICE_TYPE,
            mapOf(
                WifiDirectDnsSd.ATTR_FINGERPRINT to localPeer.fingerprint,
                WifiDirectDnsSd.ATTR_DISPLAY_NAME to localPeer.displayName.orEmpty(),
                WifiDirectDnsSd.ATTR_PORT to port.toString(),
            ),
        )
        p2pManager.clearLocalServices(
            p2pChannel,
            actionListener(
                onSuccess = { addLocalService(p2pManager, p2pChannel, serviceInfo, port) },
                onFailure = { reason ->
                    updateDiagnostics {
                        it.copy(
                            registrationState = "clear-local-services-warning:$reason",
                            lastError = "wifi-direct-clear-local-services-failed:$reason",
                        )
                    }
                    addLocalService(p2pManager, p2pChannel, serviceInfo, port)
                },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun addLocalService(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        serviceInfo: WifiP2pDnsSdServiceInfo,
        port: Int,
    ) {
        p2pManager.addLocalService(
            p2pChannel,
            serviceInfo,
            actionListener(
                onSuccess = {
                    updateDiagnostics {
                        it.copy(
                            registrationState = "registered:$SERVICE_TYPE",
                            localPort = port,
                            localAddresses = localIpv4Addresses(),
                            lastError = null,
                        )
                    }
                },
                onFailure = { reason ->
                    updateDiagnostics {
                        it.copy(
                            registrationState = "registration-failed:$reason",
                            lastError = "wifi-direct-registration-failed:$reason",
                        )
                    }
                },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun discoverServices(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
    ) {
        p2pManager.setDnsSdResponseListeners(
            p2pChannel,
            { instanceName, registrationType, device ->
                if (registrationType.contains(SERVICE_TYPE) || instanceName.startsWith(SERVICE_INSTANCE_PREFIX)) {
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "service-found:${device.deviceName}",
                            p2pServiceFoundCount = it.p2pServiceFoundCount + 1,
                        )
                    }
                }
            },
            { fullDomainName, record, device ->
                if (WifiDirectDnsSd.isKrakenService(fullDomainName, record)) {
                    upsertDiscoveredPeer(device, fullDomainName, record)
                }
            },
        )
        val request = WifiP2pDnsSdServiceRequest.newInstance()
        serviceRequest = request
        requestVisiblePeers("initial")
        p2pManager.clearServiceRequests(
            p2pChannel,
            actionListener(
                onSuccess = { addServiceRequestAndDiscover(p2pManager, p2pChannel, request) },
                onFailure = { reason ->
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "clear-service-requests-warning:$reason",
                            lastError = "wifi-direct-clear-service-requests-failed:$reason",
                        )
                    }
                    addServiceRequestAndDiscover(p2pManager, p2pChannel, request)
                },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun addServiceRequestAndDiscover(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        request: WifiP2pDnsSdServiceRequest,
    ) {
        p2pManager.addServiceRequest(
            p2pChannel,
            request,
            actionListener(
                onSuccess = {
                    p2pManager.discoverServices(
                        p2pChannel,
                        actionListener(
                            onSuccess = {
                                updateDiagnostics {
                                    it.copy(
                                        discoveryState = "discovering:$SERVICE_TYPE",
                                        discoveryCycleCount = it.discoveryCycleCount + 1,
                                        lastError = null,
                                    )
                                }
                            },
                            onFailure = { reason ->
                                updateDiagnostics {
                                    it.copy(
                                        discoveryState = "discover-failed:$reason",
                                        lastError = "wifi-direct-discover-failed:$reason",
                                    )
                                }
                            },
                        ),
                    )
                },
                onFailure = { reason ->
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "service-request-failed:$reason",
                            lastError = "wifi-direct-service-request-failed:$reason",
                        )
                    }
                },
            ),
        )
    }

    private fun startRediscoveryLoop() {
        rediscoveryThread = Thread(
            {
                while (running.get()) {
                    try {
                        Thread.sleep(REDISCOVERY_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    if (running.get()) {
                        restartDiscoveryCycle("periodic")
                    }
                }
            },
            "kraken-wifi-direct-rediscover",
        ).also {
            it.isDaemon = true
            it.start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartDiscoveryCycle(reason: String) {
        if (p2pNegotiationInProgress.get() && !reason.startsWith("connect-retry")) {
            updateDiagnostics {
                it.copy(discoveryState = "rediscovery-skipped:p2p-negotiation:$reason")
            }
            return
        }
        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        requestVisiblePeers(reason)
        if (hasOnlyInvitedVisibleDevices()) {
            resetStaleP2pNegotiation(p2pManager, p2pChannel, "stale-invited:$reason")
        }
        updateDiagnostics {
            it.copy(discoveryCycleCount = it.discoveryCycleCount + 1)
        }
        p2pManager.discoverPeers(
            p2pChannel,
            actionListener(
                onSuccess = {
                    updateDiagnostics {
                        it.copy(discoveryState = "peer-discovery:$reason", lastError = null)
                    }
                    rediscoverServices(p2pManager, p2pChannel, reason)
                },
                onFailure = { peerReason ->
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "peer-discovery-warning:$reason:$peerReason",
                            lastError = "wifi-direct-discover-peers-failed:$reason:$peerReason",
                        )
                    }
                    rediscoverServices(p2pManager, p2pChannel, reason)
                },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun rediscoverServices(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        reason: String,
    ) {
        p2pManager.discoverServices(
            p2pChannel,
            actionListener(
                onSuccess = {
                    updateDiagnostics {
                        it.copy(discoveryState = "rediscovering:$reason:$SERVICE_TYPE", lastError = null)
                    }
                },
                onFailure = { serviceReason ->
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "rediscover-failed:$reason:$serviceReason",
                            lastError = "wifi-direct-rediscover-services-failed:$reason:$serviceReason",
                        )
                    }
                },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun resetStaleP2pNegotiation(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        reason: String,
    ) {
        val cancelResult = cancelConnectBlocking(p2pManager, p2pChannel, reason)
        val removeResult = removeExistingGroupBlocking(p2pManager, p2pChannel, reason)
        updateDiagnostics {
            it.copy(
                discoveryState = "stale-p2p-reset-complete:$reason:cancel=$cancelResult:group=$removeResult",
                wifiDirectGroupFormed = if (removeResult.startsWith("removed") || removeResult == "none") {
                    false
                } else {
                    it.wifiDirectGroupFormed
                },
                wifiDirectGroupRole = if (removeResult.startsWith("removed") || removeResult == "none") {
                    "none"
                } else {
                    it.wifiDirectGroupRole
                },
                lastError = if (cancelResult.startsWith("failed") || removeResult.startsWith("failed")) {
                    "wifi-direct-stale-reset:$reason:cancel=$cancelResult:group=$removeResult"
                } else {
                    it.lastError
                },
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun cancelConnectBlocking(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        reason: String,
    ): String {
        val latch = CountDownLatch(1)
        var result = "timeout"
        p2pManager.cancelConnect(
            p2pChannel,
            actionListener(
                onSuccess = {
                    result = "cancelled"
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "stale-p2p-connect-cancelled:$reason",
                            lastError = null,
                        )
                    }
                    latch.countDown()
                },
                onFailure = { cancelReason ->
                    result = "failed:$cancelReason"
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "stale-p2p-connect-cancel-warning:$reason:$cancelReason",
                        )
                    }
                    latch.countDown()
                },
            ),
        )
        if (!latch.await(P2P_RESET_ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            updateDiagnostics {
                it.copy(discoveryState = "stale-p2p-connect-cancel-timeout:$reason")
            }
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun removeExistingGroupBlocking(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        reason: String,
    ): String {
        val group = requestGroupInfoBlocking() ?: return "none"
        val latch = CountDownLatch(1)
        var result = "timeout"
        p2pManager.removeGroup(
            p2pChannel,
            actionListener(
                onSuccess = {
                    result = "removed"
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "stale-p2p-group-removed:$reason",
                            wifiDirectGroupFormed = false,
                            wifiDirectGroupRole = "none",
                            lastError = null,
                        )
                    }
                    latch.countDown()
                },
                onFailure = { removeReason ->
                    result = "failed:$removeReason"
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "stale-p2p-group-remove-warning:$reason:$removeReason",
                        )
                    }
                    latch.countDown()
                },
            ),
        )
        if (!latch.await(P2P_RESET_ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            updateDiagnostics {
                it.copy(discoveryState = "stale-p2p-group-remove-timeout:$reason")
            }
        }
        return "$result:${group.networkName.orEmpty()}"
    }

    @SuppressLint("MissingPermission")
    private fun requestVisiblePeers(reason: String) {
        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        p2pManager.requestPeers(p2pChannel) { deviceList ->
            updateVisibleDevices(deviceList.deviceList.toList(), reason)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestVisiblePeersBlocking(reason: String): List<WifiDirectVisibleDevice> {
        val p2pManager = manager ?: return visibleDeviceSnapshot()
        val p2pChannel = channel ?: return visibleDeviceSnapshot()
        val latch = CountDownLatch(1)
        p2pManager.requestPeers(p2pChannel) { deviceList ->
            updateVisibleDevices(deviceList.deviceList.toList(), reason)
            latch.countDown()
        }
        latch.await(PEER_VISIBILITY_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return visibleDeviceSnapshot()
    }

    private fun updateVisibleDevices(devices: List<WifiP2pDevice>, reason: String) {
        val count = devices.size
        synchronized(visibleDevices) {
            visibleDevices.clear()
            visibleDevices.addAll(
                devices.map { device ->
                    WifiDirectVisibleDevice(
                        deviceAddress = device.deviceAddress,
                        deviceName = device.deviceName,
                        status = wifiP2pDeviceStatusName(device.status),
                    )
                },
            )
        }
        updateDiagnostics {
            it.copy(
                p2pVisibleDeviceCount = count,
                p2pUnboundVisibleDeviceCount = (count - peerCount()).coerceAtLeast(0),
                wifiDirectVisibleDevices = visibleDeviceDiagnostics(),
                discoveryState = if (count > 0 && peerCount() == 0) {
                    "p2p-devices-visible:$reason:$count"
                } else {
                    it.discoveryState
                },
            )
        }
    }

    private fun acceptLoop(socket: WifiDirectServerSocket) {
        while (running.get()) {
            try {
                socket.serverSocket.accept().use { client ->
                    client.soTimeout = READ_TIMEOUT_MS
                    val envelope = LanFrameCodec.decodeEnvelope(client.getInputStream())
                    val replyPort = envelope.senderReplyPort
                    if (replyPort != null && replyPort > 0) {
                        upsertPeer(
                            WifiDirectPeer(
                                peer = DiscoveredPeer(
                                    peerId = envelope.senderPeerId,
                                    fingerprint = envelope.senderFingerprint,
                                    displayName = envelope.senderDisplayName,
                                ),
                                deviceAddress = "$INBOUND_DEVICE_ADDRESS_PREFIX${envelope.senderFingerprint}",
                                deviceName = envelope.senderDisplayName,
                                port = replyPort,
                                host = client.inetAddress,
                                bindingSource = "inbound-socket",
                                bindingState = "BOUND",
                            ),
                        )
                    }
                    incoming += ReceivedPacket(
                        fromPeer = DiscoveredPeer(
                            peerId = envelope.senderPeerId,
                            fingerprint = envelope.senderFingerprint,
                            displayName = envelope.senderDisplayName,
                        ),
                        packet = envelope.packet,
                        receivedAtEpochMillis = clock(),
                    )
                    updateDiagnostics {
                        it.copy(
                            acceptedConnections = it.acceptedConnections + 1,
                            inboundPackets = it.inboundPackets + 1,
                            lastError = null,
                        )
                    }
                    LanFrameCodec.writeAck(client.getOutputStream())
                }
            } catch (_: SocketTimeoutException) {
                // Expected accept timeout; loop re-checks service state.
            } catch (error: Exception) {
                updateDiagnostics {
                    it.copy(
                        malformedFramesDropped = it.malformedFramesDropped + 1,
                        lastError = "wifi-direct-accept:${error.message ?: error::class.java.simpleName}",
                    )
                }
            }
        }
    }

    private fun newWifiDirectServerSocket(): WifiDirectServerSocket {
        val bindAddress = (p2pIpv4Addresses().firstOrNull() ?: "0.0.0.0")
            .let(InetAddress::getByName)
        val serverSocket = runCatching {
            ServerSocket(DEFAULT_WIFI_DIRECT_PORT, SERVER_BACKLOG, bindAddress)
        }.getOrElse { error ->
            updateDiagnostics {
                it.copy(lastError = "wifi-direct-fixed-port-unavailable:${error.message ?: error::class.java.simpleName}")
            }
            ServerSocket(0, SERVER_BACKLOG, bindAddress)
        }.also { it.soTimeout = ACCEPT_TIMEOUT_MS }
        val localAddress = serverSocket.localSocketAddress as InetSocketAddress
        return WifiDirectServerSocket(
            serverSocket = serverSocket,
            bindAddress = localAddress.address ?: bindAddress,
            port = localAddress.port,
        )
    }

    private fun sendToPeer(peer: WifiDirectPeer, packet: KrakenPacket): TransportSendResult =
        runCatching {
            val host = requireNotNull(peer.host) { "wifi-direct-send-host-missing" }
            updateDiagnostics {
                it.copy(
                    wifiDirectLastSendHost = host.hostAddress,
                    wifiDirectLastSendPort = peer.port,
                )
            }
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, peer.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                socket.getOutputStream().write(
                    LanFrameCodec.encode(
                        packet = packet,
                        senderPeer = localPeer,
                        senderReplyPort = serverSocket?.port,
                    ),
                )
                socket.getOutputStream().flush()
                LanFrameCodec.readAck(socket.getInputStream())
            }
            updateDiagnostics { it.copy(lastError = null) }
            TransportSendResult(true)
        }.getOrElse { error ->
            val reason = error.message ?: "wifi-direct-send-failed"
            recordSendFailure(reason)
            TransportSendResult(false, reason)
        }

    @SuppressLint("MissingPermission")
    private fun resolveSendPeer(peer: WifiDirectPeer, allowCachedHost: Boolean): WifiDirectPeer? {
        if (allowCachedHost && peer.host != null) {
            return peer
        }
        connectionHostFor(peer)?.let { host ->
            return peer.copy(host = host)
        }
        if (!peer.deviceAddress.startsWith(INBOUND_DEVICE_ADDRESS_PREFIX) &&
            !peer.deviceAddress.startsWith(FALLBACK_DEVICE_ADDRESS_PREFIX)
        ) {
            connectAndWaitForConnectionHost(peer)?.let { host ->
                return peer.copy(host = host)
            }
        }
        return null
    }

    private fun connectAndWaitForConnectionHost(peer: WifiDirectPeer): InetAddress? {
        p2pNegotiationInProgress.set(true)
        return try {
            requestPeerConnectionWithRetry(peer)
            waitForConnectionHost(peer)
        } finally {
            p2pNegotiationInProgress.set(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeerConnectionWithRetry(peer: WifiDirectPeer): WifiDirectConnectResult {
        val p2pManager = manager ?: return WifiDirectConnectResult.Unavailable
        val p2pChannel = channel ?: return WifiDirectConnectResult.Unavailable
        var lastResult: WifiDirectConnectResult = WifiDirectConnectResult.Unavailable
        repeat(CONNECT_REQUEST_ATTEMPTS) { attempt ->
            val attemptNumber = attempt + 1
            val groupOwnerIntent = CONNECT_GROUP_OWNER_INTENTS[attempt % CONNECT_GROUP_OWNER_INTENTS.size]
            lastResult = requestPeerConnectionBlocking(
                p2pManager = p2pManager,
                p2pChannel = p2pChannel,
                peer = peer,
                attempt = attemptNumber,
                groupOwnerIntent = groupOwnerIntent,
            )
            if (lastResult == WifiDirectConnectResult.Accepted) return lastResult
            if (!lastResult.shouldRetryConnectFailure()) return lastResult
            resetStaleP2pNegotiation(p2pManager, p2pChannel, "connect-retry:${peer.deviceAddress}:attempt-$attemptNumber")
            restartDiscoveryCycle("connect-retry:${peer.deviceAddress}:attempt-$attemptNumber")
            requestVisiblePeersBlocking("connect-retry:$attemptNumber")
            Thread.sleep(CONNECT_RETRY_DELAY_MS)
        }
        return lastResult
    }

    private fun requestPeerConnectionBlocking(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        peer: WifiDirectPeer,
        attempt: Int,
        groupOwnerIntent: Int,
    ): WifiDirectConnectResult {
        if (peer.deviceAddress.startsWith(INBOUND_DEVICE_ADDRESS_PREFIX)) return WifiDirectConnectResult.Unavailable
        if (peer.deviceAddress.startsWith(FALLBACK_DEVICE_ADDRESS_PREFIX)) return WifiDirectConnectResult.Unavailable
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            this.groupOwnerIntent = groupOwnerIntent
        }
        val stopPeerDiscoveryResult = stopPeerDiscoveryBeforeConnect(
            p2pManager = p2pManager,
            p2pChannel = p2pChannel,
            attempt = attempt,
            groupOwnerIntent = groupOwnerIntent,
            peer = peer,
        )
        val preConnectCancelResult = if (stopPeerDiscoveryResult.isRetryableP2pActionFailure()) {
            cancelConnectBlocking(
                p2pManager = p2pManager,
                p2pChannel = p2pChannel,
                reason = "pre-connect-after-stop-warning:${peer.deviceAddress}:attempt-$attempt",
            )
        } else {
            null
        }
        val latch = CountDownLatch(1)
        var failureReason: Int? = null
        val requestedResult = WifiDirectConnectDiagnostics.resultLabel(
            result = "requested",
            attempt = attempt,
            groupOwnerIntent = groupOwnerIntent,
        )
        updateDiagnostics {
            it.copy(
                discoveryState = "connect-requested:$attempt:$groupOwnerIntent:${peer.deviceName ?: peer.deviceAddress}",
                wifiDirectLastConnectDeviceAddress = peer.deviceAddress,
                wifiDirectLastConnectDeviceName = peer.deviceName,
                wifiDirectLastConnectGroupOwnerIntent = groupOwnerIntent,
                wifiDirectLastConnectResult = requestedResult,
                wifiDirectLastConnectFailureReason = null,
                wifiDirectConnectAttempts = connectAttemptDiagnosticsSnapshot(),
            )
        }
        p2pManager.connect(
            p2pChannel,
            config,
                actionListener(
                    onSuccess = {
                        val acceptedResult = WifiDirectConnectDiagnostics.resultLabel(
                            result = "accepted",
                            attempt = attempt,
                            groupOwnerIntent = groupOwnerIntent,
                        )
                        recordConnectAttempt(
                            attempt = attempt,
                            groupOwnerIntent = groupOwnerIntent,
                            result = acceptedResult,
                            failureReason = null,
                            stopPeerDiscoveryResult = stopPeerDiscoveryResult,
                            preConnectCancelResult = preConnectCancelResult,
                        )
                        updateDiagnostics {
                            it.copy(
                                discoveryState = "connect-request-accepted:$attempt:$groupOwnerIntent:${peer.deviceName ?: peer.deviceAddress}",
                                wifiDirectLastConnectGroupOwnerIntent = groupOwnerIntent,
                                wifiDirectLastConnectResult = acceptedResult,
                                wifiDirectLastConnectFailureReason = null,
                                wifiDirectConnectAttempts = connectAttemptDiagnosticsSnapshot(),
                                lastError = null,
                            )
                        }
                    refreshConnectionInfo()
                    latch.countDown()
                    },
                    onFailure = { reason ->
                        failureReason = reason
                        val failedResult = WifiDirectConnectDiagnostics.resultLabel(
                            result = "failed",
                            attempt = attempt,
                            groupOwnerIntent = groupOwnerIntent,
                            reason = reason,
                        )
                        recordConnectAttempt(
                            attempt = attempt,
                            groupOwnerIntent = groupOwnerIntent,
                            result = failedResult,
                            failureReason = reason,
                            stopPeerDiscoveryResult = stopPeerDiscoveryResult,
                            preConnectCancelResult = preConnectCancelResult,
                        )
                        updateDiagnostics {
                            it.copy(
                                discoveryState = "connect-request-failed:$attempt:$groupOwnerIntent:${peer.deviceName ?: peer.deviceAddress}:$reason",
                                wifiDirectLastConnectGroupOwnerIntent = groupOwnerIntent,
                                wifiDirectLastConnectResult = failedResult,
                                wifiDirectLastConnectFailureReason = reason,
                                wifiDirectConnectAttempts = connectAttemptDiagnosticsSnapshot(),
                                lastError = "wifi-direct-connect-failed:$reason",
                            )
                        }
                    latch.countDown()
                },
            ),
        )
        if (!latch.await(CONNECT_ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            val timeoutResult = WifiDirectConnectDiagnostics.resultLabel(
                result = "timeout",
                attempt = attempt,
                groupOwnerIntent = groupOwnerIntent,
            )
            recordConnectAttempt(
                attempt = attempt,
                groupOwnerIntent = groupOwnerIntent,
                result = timeoutResult,
                failureReason = null,
                stopPeerDiscoveryResult = stopPeerDiscoveryResult,
                preConnectCancelResult = preConnectCancelResult,
            )
            updateDiagnostics {
                it.copy(
                    discoveryState = "connect-request-timeout:$attempt:$groupOwnerIntent:${peer.deviceName ?: peer.deviceAddress}",
                    wifiDirectLastConnectGroupOwnerIntent = groupOwnerIntent,
                    wifiDirectLastConnectResult = timeoutResult,
                    wifiDirectConnectAttempts = connectAttemptDiagnosticsSnapshot(),
                )
            }
            return WifiDirectConnectResult.Timeout
        }
        return failureReason?.let(WifiDirectConnectResult::Failed) ?: WifiDirectConnectResult.Accepted
    }

    @SuppressLint("MissingPermission")
    private fun stopPeerDiscoveryBeforeConnect(
        p2pManager: WifiP2pManager,
        p2pChannel: WifiP2pManager.Channel,
        attempt: Int,
        groupOwnerIntent: Int,
        peer: WifiDirectPeer,
    ): String {
        val latch = CountDownLatch(1)
        var failureReason: Int? = null
        var result = "timeout"
        p2pManager.stopPeerDiscovery(
            p2pChannel,
            actionListener(
                onSuccess = {
                    result = "stopped"
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "peer-discovery-stopped-for-connect:$attempt:$groupOwnerIntent:${peer.deviceName ?: peer.deviceAddress}",
                            lastError = null,
                        )
                    }
                    latch.countDown()
                },
                onFailure = { reason ->
                    failureReason = reason
                    result = WifiDirectConnectDiagnostics.actionFailureLabel("failed", reason)
                    updateDiagnostics {
                        it.copy(
                            discoveryState = "peer-discovery-stop-warning-for-connect:$attempt:$groupOwnerIntent:${peer.deviceName ?: peer.deviceAddress}:$reason",
                        )
                    }
                    latch.countDown()
                },
            ),
        )
        if (!latch.await(CONNECT_ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            updateDiagnostics {
                it.copy(
                    discoveryState = "peer-discovery-stop-timeout-for-connect:$attempt:$groupOwnerIntent:${peer.deviceName ?: peer.deviceAddress}",
                )
            }
        } else if (failureReason in setOf(P2P_BUSY_REASON, P2P_ERROR_REASON)) {
            Thread.sleep(CONNECT_RETRY_DELAY_MS)
        }
        return result
    }

    private fun connectionHostFor(peer: WifiDirectPeer): InetAddress? {
        val info = connectionInfo ?: requestConnectionInfoBlocking()
        if (info?.groupFormed != true) return null
        return if (info.isGroupOwner) {
            peer.host ?: groupClientHostFor(peer.deviceAddress) ?: arpHostFor(peer.deviceAddress)
        } else {
            info.groupOwnerAddress
        }
    }

    private fun waitForConnectionHost(peer: WifiDirectPeer): InetAddress? {
        val deadline = clock() + GROUP_FORMATION_WAIT_MS
        while (clock() <= deadline && running.get()) {
            connectionHostFor(peer)?.let { return it }
            Thread.sleep(CONNECTION_INFO_POLL_MS)
        }
        return connectionHostFor(peer)
    }

    private fun fallbackPeerForRelationshipPeer(peer: DiscoveredPeer): WifiDirectPeer? {
        val info = connectionInfo ?: requestConnectionInfoBlocking()
        if (info?.groupFormed != true) {
            val visibleDevice = awaitVisibleDeviceForRelationshipPeer(peer, "fallback-connect") ?: return null
            val fallbackBinding = WifiDirectEndpointResolver.visibleDeviceFallback(
                peer = peer,
                visibleDevice = WifiDirectEndpointVisibleDevice(
                    deviceAddress = visibleDevice.deviceAddress,
                    deviceName = visibleDevice.deviceName,
                ),
                defaultPort = DEFAULT_WIFI_DIRECT_PORT,
            )
            val tentativePeer = WifiDirectPeer(
                peer = peer,
                deviceAddress = fallbackBinding.deviceAddress ?: visibleDevice.deviceAddress,
                deviceName = fallbackBinding.deviceName,
                port = fallbackBinding.port ?: DEFAULT_WIFI_DIRECT_PORT,
                host = null,
                bindingSource = fallbackBinding.source,
                bindingState = fallbackBinding.state.name,
                bindingReason = fallbackBinding.reason,
            )
            upsertPeer(tentativePeer)
            val host = connectAndWaitForConnectionHost(tentativePeer) ?: return tentativePeer.also {
                recordEndpointBindingFailure(
                    peer = peer,
                    state = fallbackBinding.state.name,
                    reason = fallbackBinding.reason ?: "single-visible-device-connect-endpoint-unresolved:${visibleDevice.deviceAddress}",
                )
            }
            val connectedBinding = WifiDirectPeerBinding.bound(
                fingerprint = peer.fingerprint,
                deviceAddress = tentativePeer.deviceAddress,
                deviceName = tentativePeer.deviceName,
                host = host.hostAddress,
                port = tentativePeer.port,
                source = tentativePeer.bindingSource,
                reason = tentativePeer.bindingSource,
            )
            val connectedPeer = tentativePeer.copy(
                host = host,
                bindingSource = connectedBinding.source,
                bindingState = connectedBinding.state.name,
            )
            upsertPeer(connectedPeer)
            recordEndpointBinding(
                peer = peer,
                state = connectedBinding.state.name,
                reason = connectedBinding.reason,
                endpoint = connectedPeer,
            )
            return connectedPeer
        }
        val routeBinding = WifiDirectEndpointResolver.groupRoute(
            peer = peer,
            deviceAddress = "$FALLBACK_DEVICE_ADDRESS_PREFIX${peer.fingerprint}",
            deviceName = peer.displayName,
            port = DEFAULT_WIFI_DIRECT_PORT,
            localIsGroupOwner = info.isGroupOwner,
            groupOwnerHost = info.groupOwnerAddress?.hostAddress,
            p2pClientHost = (singleGroupClientHost() ?: arpHostFor(SINGLE_P2P_CLIENT_DEVICE_ADDRESS))?.hostAddress,
        )
        val host = routeBinding.host?.let { hostAddress ->
            runCatching { InetAddress.getByName(hostAddress) }.getOrNull()
        } ?: return null.also {
            recordEndpointBindingFailure(
                peer = peer,
                state = routeBinding.state.name,
                reason = routeBinding.reason ?: "fallback-host-unavailable",
            )
        }
        val fallback = WifiDirectPeer(
            peer = peer,
            deviceAddress = routeBinding.deviceAddress ?: "$FALLBACK_DEVICE_ADDRESS_PREFIX${peer.fingerprint}",
            deviceName = routeBinding.deviceName,
            port = routeBinding.port ?: DEFAULT_WIFI_DIRECT_PORT,
            host = host,
            bindingSource = routeBinding.source,
            bindingState = routeBinding.state.name,
        )
        upsertPeer(fallback)
        recordEndpointBinding(
            peer = peer,
            state = routeBinding.state.name,
            reason = routeBinding.reason,
            endpoint = fallback,
        )
        updateDiagnostics {
            it.copy(
                discoveryState = if (info.isGroupOwner) {
                    "peer-fallback:p2p-client-host"
                } else {
                    "peer-fallback:group-owner-address"
                },
                discoveredPeerCount = peerCount(),
                wifiDirectLastBindingError = "wifi-direct-dns-sd-fallback",
                wifiDirectDiscoveredPeers = peerEndpointDiagnostics(),
                wifiDirectBoundEndpoints = boundEndpointDiagnostics(),
            )
        }
        return fallback
    }

    private fun groupClientHostFor(deviceAddress: String): InetAddress? {
        val group = requestGroupInfoBlocking() ?: return null
        val normalizedAddress = normalizeDeviceAddress(deviceAddress)
        val clients = group.clientList.toList()
        val client = clients.firstOrNull { client ->
            normalizedAddress != null && normalizeDeviceAddress(client.deviceAddress) == normalizedAddress
        } ?: clients.singleOrNull()
        return client?.let(::wifiP2pDeviceIpAddress)
    }

    private fun singleGroupClientHost(): InetAddress? {
        val group = requestGroupInfoBlocking() ?: return null
        return group.clientList.toList().singleOrNull()?.let(::wifiP2pDeviceIpAddress)
    }

    private fun arpHostFor(deviceAddress: String): InetAddress? =
        runCatching {
            val arpTable = File("/proc/net/arp").readText()
            WifiDirectArpTable.hostForDeviceAddress(arpTable, deviceAddress)
                ?: WifiDirectArpTable.singleP2pClientHost(arpTable)
        }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfoBlocking(): WifiP2pInfo? {
        val p2pManager = manager ?: return connectionInfo
        val p2pChannel = channel ?: return connectionInfo
        val latch = CountDownLatch(1)
        var latest: WifiP2pInfo? = null
        p2pManager.requestConnectionInfo(p2pChannel) { info ->
            latest = info
            connectionInfo = info
            updateDiagnostics {
                val p2pAddresses = p2pIpv4Addresses()
                it.copy(
                    discoveryState = if (info?.groupFormed == true) "connected" else it.discoveryState,
                    wifiDirectGroupFormed = info?.groupFormed,
                    wifiDirectIsGroupOwner = info?.isGroupOwner,
                    wifiDirectGroupRole = wifiDirectGroupRole(info),
                    wifiDirectGroupOwnerAddress = info?.groupOwnerAddress?.hostAddress,
                    wifiDirectLocalP2pAddress = p2pAddresses.firstOrNull(),
                    p2pInterfaceAddresses = p2pAddresses,
                    lastError = if (info?.groupFormed == true) null else it.lastError,
                )
            }
            latch.countDown()
        }
        latch.await(CONNECTION_INFO_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return latest ?: connectionInfo
    }

    @SuppressLint("MissingPermission")
    private fun requestGroupInfoBlocking(): WifiP2pGroup? {
        val p2pManager = manager ?: return null
        val p2pChannel = channel ?: return null
        val latch = CountDownLatch(1)
        var latest: WifiP2pGroup? = null
        p2pManager.requestGroupInfo(p2pChannel) { group ->
            latest = group
            latch.countDown()
        }
        latch.await(CONNECTION_INFO_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return latest
    }

    @SuppressLint("MissingPermission")
    private fun refreshConnectionInfo() {
        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        p2pManager.requestConnectionInfo(p2pChannel) { info ->
            connectionInfo = info
            updateDiagnostics {
                val p2pAddresses = p2pIpv4Addresses()
                it.copy(
                    discoveryState = if (info?.groupFormed == true) "connected" else it.discoveryState,
                    wifiDirectGroupFormed = info?.groupFormed,
                    wifiDirectIsGroupOwner = info?.isGroupOwner,
                    wifiDirectGroupRole = wifiDirectGroupRole(info),
                    wifiDirectGroupOwnerAddress = info?.groupOwnerAddress?.hostAddress,
                    wifiDirectLocalP2pAddress = p2pAddresses.firstOrNull(),
                    p2pInterfaceAddresses = p2pAddresses,
                    lastError = if (info?.groupFormed == true) null else it.lastError,
                )
            }
        }
    }

    private fun upsertDiscoveredPeer(
        device: WifiP2pDevice,
        fullDomainName: String?,
        record: Map<String, String>,
    ) {
        updateDiagnostics { it.copy(p2pTxtRecordCount = it.p2pTxtRecordCount + 1) }
        val binding = WifiDirectEndpointResolver.fromTxtRecord(
            deviceAddress = device.deviceAddress,
            deviceName = device.deviceName,
            record = record,
            localFingerprint = localPeer.fingerprint,
        )
        when (binding) {
            is WifiDirectEndpointResolveResult.Rejected -> {
                val error = "wifi-direct-kraken-txt-${binding.reason}"
                appendTxtRecordDiagnostic(
                    WifiDirectTxtRecordDiagnostic(
                        deviceAddress = device.deviceAddress,
                        deviceName = device.deviceName,
                        fingerprintPrefix = WifiDirectDnsSd.fingerprint(record)?.fingerprintPrefix(),
                        port = WifiDirectDnsSd.port(record),
                        keys = record.keys.sorted(),
                        accepted = false,
                        reason = binding.reason,
                    ),
                )
                updateDiagnostics {
                    it.copy(
                        discoveryState = "kraken-txt-rejected:${device.deviceName}:${binding.reason}",
                        p2pTxtRejectedCount = it.p2pTxtRejectedCount + 1,
                        wifiDirectLastBindingError = error,
                        wifiDirectTxtRecords = synchronized(txtRecordDiagnostics) { txtRecordDiagnostics.toList() },
                        lastError = error,
                    )
                }
                return
            }
            is WifiDirectEndpointResolveResult.Resolved -> {
                appendTxtRecordDiagnostic(
                    WifiDirectTxtRecordDiagnostic(
                        deviceAddress = binding.binding.deviceAddress ?: device.deviceAddress,
                        deviceName = binding.binding.deviceName,
                        fingerprintPrefix = binding.binding.fingerprint.fingerprintPrefix(),
                        port = binding.binding.port,
                        keys = record.keys.sorted(),
                        accepted = true,
                        reason = null,
                    ),
                )
                upsertPeer(
                    WifiDirectPeer(
                        peer = DiscoveredPeer(
                            peerId = binding.peerId,
                            fingerprint = binding.binding.fingerprint,
                            displayName = binding.displayName,
                        ),
                        deviceAddress = binding.binding.deviceAddress ?: device.deviceAddress,
                        deviceName = binding.binding.deviceName,
                        port = binding.binding.port ?: DEFAULT_WIFI_DIRECT_PORT,
                        host = null,
                        bindingSource = binding.binding.source,
                        bindingState = binding.binding.state.name,
                        bindingReason = binding.binding.reason,
                    ),
                )
            }
        }
        updateDiagnostics {
            it.copy(
                discoveryState = "peer-found:${device.deviceName}:${fullDomainName.orEmpty()}",
                discoveredPeerCount = peerCount(),
                p2pTxtBoundPeerCount = it.p2pTxtBoundPeerCount + 1,
                p2pUnboundVisibleDeviceCount = (it.p2pVisibleDeviceCount - peerCount()).coerceAtLeast(0),
                wifiDirectLastBindingError = null,
                wifiDirectDiscoveredPeers = peerEndpointDiagnostics(),
                wifiDirectTxtRecords = synchronized(txtRecordDiagnostics) { txtRecordDiagnostics.toList() },
                wifiDirectBoundEndpoints = boundEndpointDiagnostics(),
                lastError = null,
            )
        }
    }

    private fun upsertPeer(peer: WifiDirectPeer) {
        synchronized(peers) {
            val existing = peers.values.firstOrNull {
                it.peer.fingerprint == peer.peer.fingerprint ||
                    it.deviceAddress == peer.deviceAddress
            }
            val merged = peer.copy(
                deviceAddress = if (peer.deviceAddress.startsWith(INBOUND_DEVICE_ADDRESS_PREFIX)) {
                    existing?.deviceAddress ?: peer.deviceAddress
                } else {
                    peer.deviceAddress
                },
                deviceName = peer.deviceName ?: existing?.deviceName,
                host = peer.host ?: existing?.host,
                bindingSource = peer.bindingSource,
                bindingState = if (peer.host != null || existing?.host != null) "BOUND" else peer.bindingState,
                bindingReason = peer.bindingReason ?: existing?.bindingReason,
            )
            peers.entries.removeAll {
                it.value.peer.fingerprint == peer.peer.fingerprint ||
                    it.value.deviceAddress == peer.deviceAddress
            }
            peers[merged.peer.peerId] = merged
        }
    }

    private fun peerCount(): Int =
        synchronized(peers) { peers.size }

    private fun recordEndpointBinding(
        peer: DiscoveredPeer,
        state: String,
        reason: String?,
        endpoint: WifiDirectPeer?,
    ) {
        updateDiagnostics {
            it.copy(
                wifiDirectEndpointBindingState = state,
                wifiDirectEndpointBindingReason = reason,
                wifiDirectRelationshipPeerFingerprintPrefix = peer.fingerprint.fingerprintPrefix(),
                wifiDirectLastSendHost = endpoint?.host?.hostAddress ?: it.wifiDirectLastSendHost,
                wifiDirectLastSendPort = endpoint?.port ?: it.wifiDirectLastSendPort,
                wifiDirectDiscoveredPeers = peerEndpointDiagnostics(),
                wifiDirectVisibleDevices = visibleDeviceDiagnostics(),
                wifiDirectTxtRecords = synchronized(txtRecordDiagnostics) { txtRecordDiagnostics.toList() },
                wifiDirectBoundEndpoints = boundEndpointDiagnostics(),
            )
        }
    }

    private fun recordEndpointBindingFailure(peer: DiscoveredPeer, state: String, reason: String) {
        updateDiagnostics {
            it.copy(
                wifiDirectEndpointBindingState = state,
                wifiDirectEndpointBindingReason = reason,
                wifiDirectRelationshipPeerFingerprintPrefix = peer.fingerprint.fingerprintPrefix(),
                wifiDirectDiscoveredPeers = peerEndpointDiagnostics(),
                wifiDirectVisibleDevices = visibleDeviceDiagnostics(),
                wifiDirectTxtRecords = synchronized(txtRecordDiagnostics) { txtRecordDiagnostics.toList() },
                wifiDirectBoundEndpoints = boundEndpointDiagnostics(),
            )
        }
    }

    private fun currentEndpointBindingFailure(
        peer: DiscoveredPeer,
        defaultState: String,
        defaultReason: String,
    ): Pair<String, String> {
        val snapshot = diagnostics
        val sameRelationshipPeer =
            snapshot.wifiDirectRelationshipPeerFingerprintPrefix == peer.fingerprint.fingerprintPrefix()
        val state = snapshot.wifiDirectEndpointBindingState
            .takeIf { sameRelationshipPeer && it.isNotBlank() }
            ?: defaultState
        val reason = snapshot.wifiDirectEndpointBindingReason
            ?.takeIf { sameRelationshipPeer && it.isNotBlank() }
            ?: defaultReason
        return state to reason
    }

    private fun missingPeerReason(peer: DiscoveredPeer): String {
        val info = connectionInfo ?: requestConnectionInfoBlocking()
        return when {
            info?.groupFormed != true -> "relationship-peer-not-seen-and-p2p-group-not-formed"
            synchronized(txtRecordDiagnostics) { txtRecordDiagnostics.none { it.accepted } } ->
                "relationship-peer-not-seen-no-accepted-txt-records"
            else -> "relationship-peer-not-seen-by-wifi-direct-transport:${peer.fingerprint.fingerprintPrefix()}"
        }
    }

    private fun endpointUnavailableReason(peer: WifiDirectPeer): String {
        val info = connectionInfo ?: requestConnectionInfoBlocking()
        val connectResult = diagnostics.wifiDirectLastConnectResult
            ?.let { ":connect=$it" }
            .orEmpty()
        return when {
            info?.groupFormed != true -> "p2p-group-not-formed$connectResult"
            peer.deviceAddress.startsWith(INBOUND_DEVICE_ADDRESS_PREFIX) -> "inbound-peer-host-stale-or-missing$connectResult"
            peer.deviceAddress.startsWith(FALLBACK_DEVICE_ADDRESS_PREFIX) -> "fallback-host-unavailable$connectResult"
            else -> "dns-sd-peer-discovered-but-endpoint-unresolved:${peer.deviceAddress}$connectResult"
        }
    }

    private fun peerEndpointDiagnostics(): List<WifiDirectPeerEndpointDiagnostic> =
        synchronized(peers) {
            peers.values.map { peer ->
                WifiDirectPeerEndpointDiagnostic(
                    fingerprintPrefix = peer.peer.fingerprint.fingerprintPrefix(),
                    deviceAddress = peer.deviceAddress,
                    deviceName = peer.deviceName,
                    host = peer.host?.hostAddress,
                    port = peer.port,
                    bindingState = if (peer.host != null) "BOUND" else peer.bindingState,
                    bindingSource = peer.bindingSource,
                    bindingReason = peer.bindingReason,
                )
            }
        }

    private fun boundEndpointDiagnostics(): List<WifiDirectBoundEndpointDiagnostic> =
        synchronized(peers) {
            peers.values.mapNotNull { peer ->
                val host = peer.host?.hostAddress ?: return@mapNotNull null
                WifiDirectBoundEndpointDiagnostic(
                    fingerprintPrefix = peer.peer.fingerprint.fingerprintPrefix(),
                    deviceAddress = peer.deviceAddress,
                    deviceName = peer.deviceName,
                    host = host,
                    port = peer.port,
                    bindingSource = peer.bindingSource,
                )
            }
        }

    private fun visibleDeviceDiagnostics(): List<WifiDirectVisibleDeviceDiagnostic> =
        synchronized(visibleDevices) {
            visibleDevices.map { device ->
                WifiDirectVisibleDeviceDiagnostic(
                    deviceAddress = device.deviceAddress,
                    deviceName = device.deviceName,
                    status = device.status,
                )
            }
        }

    private fun visibleDeviceForRelationshipPeer(peer: DiscoveredPeer): WifiDirectVisibleDevice? =
        synchronized(visibleDevices) {
            val displayName = peer.displayName?.trim()?.lowercase().orEmpty()
            if (displayName.isBlank()) return@synchronized null
            val namedMatches = visibleDevices.filter { device ->
                val deviceName = device.deviceName?.trim()?.lowercase().orEmpty()
                device.status in listOf("available", "connected", "invited") &&
                    deviceName.isNotBlank() &&
                    (deviceName.contains(displayName) || displayName.contains(deviceName))
            }
            namedMatches.singleOrNull()
        }

    private fun awaitVisibleDeviceForRelationshipPeer(
        peer: DiscoveredPeer,
        reason: String,
    ): WifiDirectVisibleDevice? {
        visibleDeviceForRelationshipPeer(peer)?.let { return it }
        val p2pManager = manager
        val p2pChannel = channel
        if (p2pManager == null || p2pChannel == null) {
            recordEndpointBindingFailure(peer, "FAILED", "wifi-direct-channel-unavailable-for-visible-peer-wait")
            return null
        }
        val deadline = clock() + PEER_VISIBILITY_WAIT_MS
        var cycle = 0
        while (clock() <= deadline && running.get()) {
            cycle += 1
            requestVisiblePeersBlocking("$reason:$cycle")
            visibleDeviceForRelationshipPeer(peer)?.let { device ->
                updateDiagnostics {
                    it.copy(
                        discoveryState = "relationship-peer-visible:$reason:$cycle:${device.deviceName ?: device.deviceAddress}",
                        wifiDirectVisibleDevices = visibleDeviceDiagnostics(),
                        lastError = null,
                    )
                }
                return device
            }
            p2pManager.discoverPeers(
                p2pChannel,
                actionListener(
                    onSuccess = {
                        updateDiagnostics {
                            it.copy(discoveryState = "relationship-peer-discovery:$reason:$cycle", lastError = null)
                        }
                        rediscoverServices(p2pManager, p2pChannel, "relationship-peer:$reason:$cycle")
                    },
                    onFailure = { peerReason ->
                        updateDiagnostics {
                            it.copy(
                                discoveryState = "relationship-peer-discovery-warning:$reason:$cycle:$peerReason",
                                lastError = "wifi-direct-discover-peers-failed:$reason:$cycle:$peerReason",
                            )
                        }
                        rediscoverServices(p2pManager, p2pChannel, "relationship-peer:$reason:$cycle")
                    },
                ),
            )
            Thread.sleep(PEER_VISIBILITY_POLL_MS)
        }
        recordEndpointBindingFailure(
            peer = peer,
            state = "UNSEEN",
            reason = "relationship-peer-not-visible-after-fresh-discovery:${peer.displayName.orEmpty()}",
        )
        return null
    }

    private fun visibleDeviceSnapshot(): List<WifiDirectVisibleDevice> =
        synchronized(visibleDevices) { visibleDevices.toList() }

    private fun hasOnlyInvitedVisibleDevices(): Boolean =
        synchronized(visibleDevices) {
            visibleDevices.isNotEmpty() && visibleDevices.all { it.status == "invited" }
        }

    private fun appendTxtRecordDiagnostic(record: WifiDirectTxtRecordDiagnostic) {
        synchronized(txtRecordDiagnostics) {
            txtRecordDiagnostics += record
            while (txtRecordDiagnostics.size > MAX_TXT_RECORD_DIAGNOSTICS) {
                txtRecordDiagnostics.removeAt(0)
            }
        }
    }

    private fun recordConnectAttempt(
        attempt: Int,
        groupOwnerIntent: Int,
        result: String,
        failureReason: Int?,
        stopPeerDiscoveryResult: String?,
        preConnectCancelResult: String?,
    ) {
        synchronized(connectAttemptDiagnostics) {
            connectAttemptDiagnostics += WifiDirectConnectAttemptDiagnostic(
                attempt = attempt,
                groupOwnerIntent = groupOwnerIntent,
                result = result,
                failureReason = failureReason,
                failureReasonName = WifiDirectConnectDiagnostics.failureReasonName(failureReason),
                stopPeerDiscoveryResult = stopPeerDiscoveryResult,
                preConnectCancelResult = preConnectCancelResult,
            )
            while (connectAttemptDiagnostics.size > MAX_CONNECT_ATTEMPT_DIAGNOSTICS) {
                connectAttemptDiagnostics.removeAt(0)
            }
        }
    }

    private fun connectAttemptDiagnosticsSnapshot(): List<WifiDirectConnectAttemptDiagnostic> =
        synchronized(connectAttemptDiagnostics) { connectAttemptDiagnostics.toList() }

    private fun String.isRetryableP2pActionFailure(): Boolean =
        this == WifiDirectConnectDiagnostics.actionFailureLabel("failed", P2P_ERROR_REASON) ||
            this == WifiDirectConnectDiagnostics.actionFailureLabel("failed", P2P_BUSY_REASON)

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }
        val p2pReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> refreshConnectionInfo()
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        requestVisiblePeers("broadcast")
                        val p2pManager = manager
                        val p2pChannel = channel
                        if (p2pManager != null && p2pChannel != null && !p2pNegotiationInProgress.get()) {
                            rediscoverServices(p2pManager, p2pChannel, "broadcast")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device = wifiP2pDeviceFrom(intent)
                        updateDiagnostics {
                            it.copy(
                                p2pThisDeviceStatus = device?.status?.let(::wifiP2pDeviceStatusName),
                            )
                        }
                    }
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        updateDiagnostics {
                            it.copy(
                                registrationState = if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                                    it.registrationState
                                } else {
                                    "adapter-off"
                                },
                                discoveryState = if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                                    it.discoveryState
                                } else {
                                    "adapter-off"
                                },
                            )
                        }
                    }
                }
            }
        }
        receiver = p2pReceiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(p2pReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(p2pReceiver, filter)
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiP2pDeviceFrom(intent: Intent): WifiP2pDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
        } else {
            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
        }

    private fun wifiP2pDeviceIpAddress(device: WifiP2pDevice): InetAddress? =
        inetAddressFromHiddenWifiP2pMember(device, "getIpAddress")
            ?: inetAddressFromHiddenWifiP2pMember(device, "ipAddress")
            ?: ipAddressFromWifiP2pDeviceString(device.toString())

    private fun inetAddressFromHiddenWifiP2pMember(
        device: WifiP2pDevice,
        name: String,
    ): InetAddress? =
        runCatching {
            val method = device.javaClass.methods.firstOrNull { member ->
                member.name == name && member.parameterCount == 0
            }
            val value = if (method != null) {
                method.invoke(device)
            } else {
                val field = device.javaClass.getDeclaredField(name)
                field.isAccessible = true
                field.get(device)
            }
            inetAddressFromValue(value)
        }.getOrNull()

    private fun inetAddressFromValue(value: Any?): InetAddress? =
        when (value) {
            is InetAddress -> value
            is String -> value.takeIf(::isIpv4Address)?.let(InetAddress::getByName)
            else -> null
        }

    private fun ipAddressFromWifiP2pDeviceString(value: String): InetAddress? {
        val ip = WIFI_P2P_DEVICE_IP_REGEX.find(value)?.groupValues?.getOrNull(1)
            ?: return null
        return if (isIpv4Address(ip)) InetAddress.getByName(ip) else null
    }

    private fun isIpv4Address(value: String): Boolean =
        IPV4_REGEX.matches(value) &&
            value.split('.').all { octet -> octet.toIntOrNull() in 0..255 }

    private fun normalizeDeviceAddress(value: String?): String? {
        val parts = value
            ?.trim()
            ?.lowercase()
            ?.replace("-", ":")
            ?.split(':')
            ?: return null
        if (parts.size != 6 || parts.any { it.length !in 1..2 }) return null
        return parts.joinToString(":") { it.padStart(2, '0') }
    }

    private fun wifiP2pDeviceStatusName(status: Int): String =
        when (status) {
            WifiP2pDevice.AVAILABLE -> "available"
            WifiP2pDevice.INVITED -> "invited"
            WifiP2pDevice.CONNECTED -> "connected"
            WifiP2pDevice.FAILED -> "failed"
            WifiP2pDevice.UNAVAILABLE -> "unavailable"
            else -> "unknown:$status"
        }

    private fun wifiDirectGroupRole(info: WifiP2pInfo?): String =
        when {
            info?.groupFormed != true -> "none"
            info.isGroupOwner -> "owner"
            else -> "client"
        }

    private fun recordSendFailure(reason: String) {
        updateDiagnostics {
            it.copy(
                sendFailures = it.sendFailures + 1,
                lastError = "wifi-direct-send:$reason",
            )
        }
    }

    private fun localIpv4Addresses(): List<String> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { networkInterface -> networkInterface.inetAddresses.asSequence() }
                .filter { address -> address.hostAddress?.contains(':') == false && !address.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())

    private fun p2pIpv4Addresses(): List<String> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && it.name.startsWith("p2p") }
                .flatMap { networkInterface -> networkInterface.inetAddresses.asSequence() }
                .filter { address -> address.hostAddress?.contains(':') == false && !address.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())

    private fun serviceInstanceName(): String =
        "$SERVICE_INSTANCE_PREFIX${localPeer.fingerprint.filter { it.isLetterOrDigit() }.take(24)}"

    private fun updateDiagnostics(transform: (MeshTransportDiagnostics) -> MeshTransportDiagnostics) {
        diagnostics = transform(diagnostics)
    }

    private fun actionListener(
        onSuccess: () -> Unit = {},
        onFailure: (Int) -> Unit = {},
    ): WifiP2pManager.ActionListener =
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onSuccess()
            override fun onFailure(reason: Int) = onFailure(reason)
        }

    private fun noopActionListener(): WifiP2pManager.ActionListener =
        actionListener()

    private data class WifiDirectPeer(
        val peer: DiscoveredPeer,
        val deviceAddress: String,
        val deviceName: String?,
        val port: Int,
        val host: InetAddress?,
        val bindingSource: String,
        val bindingState: String,
        val bindingReason: String? = null,
    )

    private data class WifiDirectVisibleDevice(
        val deviceAddress: String,
        val deviceName: String?,
        val status: String?,
    )

    private sealed class WifiDirectConnectResult {
        val failureReason: Int?
            get() = (this as? Failed)?.reason

        fun shouldRetryConnectFailure(): Boolean =
            this == Timeout || failureReason in setOf(P2P_ERROR_REASON, P2P_BUSY_REASON)

        data object Accepted : WifiDirectConnectResult()
        data object Timeout : WifiDirectConnectResult()
        data object Unavailable : WifiDirectConnectResult()
        data class Failed(val reason: Int) : WifiDirectConnectResult()
    }

    private data class WifiDirectServerSocket(
        val serverSocket: ServerSocket,
        val bindAddress: InetAddress,
        val port: Int,
    ) {
        fun close() {
            serverSocket.close()
        }
    }

    private companion object {
        const val SERVICE_TYPE = WifiDirectDnsSd.SERVICE_TYPE
        const val SERVICE_INSTANCE_PREFIX = "Kraken-"
        const val INBOUND_DEVICE_ADDRESS_PREFIX = "inbound-"
        const val FALLBACK_DEVICE_ADDRESS_PREFIX = "fallback-"
        const val SINGLE_P2P_CLIENT_DEVICE_ADDRESS = "single-p2p-client"
        const val DEFAULT_WIFI_DIRECT_PORT = 48381
        const val SERVER_BACKLOG = 16
        const val ACCEPT_TIMEOUT_MS = 1_000
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val CONNECTION_INFO_TIMEOUT_MS = 1_500L
        const val CONNECT_ACTION_TIMEOUT_MS = 2_000L
        const val P2P_RESET_ACTION_TIMEOUT_MS = 3_000L
        const val CONNECT_REQUEST_ATTEMPTS = 3
        const val CONNECT_RETRY_DELAY_MS = 1_500L
        const val P2P_ERROR_REASON = 0
        const val P2P_BUSY_REASON = 2
        const val CONNECTION_INFO_POLL_MS = 750L
        const val GROUP_FORMATION_WAIT_MS = 45_000L
        const val CREATE_GROUP_ACTION_TIMEOUT_MS = 5_000L
        const val CREATE_GROUP_FORMATION_WAIT_MS = 12_000L
        const val PEER_VISIBILITY_REQUEST_TIMEOUT_MS = 1_500L
        const val PEER_VISIBILITY_WAIT_MS = 10_000L
        const val PEER_VISIBILITY_POLL_MS = 1_000L
        const val SEND_ENDPOINT_RETRY_COUNT = 2
        const val SEND_ENDPOINT_RETRY_DELAY_MS = 1_500L
        const val OUTBOUND_SEND_GROUP_OWNER_INTENT = 0
        val CONNECT_GROUP_OWNER_INTENTS = listOf(OUTBOUND_SEND_GROUP_OWNER_INTENT, 15, 7)
        const val REDISCOVERY_INTERVAL_MS = 15_000L
        const val MAX_TXT_RECORD_DIAGNOSTICS = 12
        const val MAX_CONNECT_ATTEMPT_DIAGNOSTICS = 12
        val IPV4_REGEX = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
        val WIFI_P2P_DEVICE_IP_REGEX = Regex("""ipAddress:\s*(\d{1,3}(?:\.\d{1,3}){3})""")
    }
}
