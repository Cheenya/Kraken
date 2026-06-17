package com.disser.kraken.mesh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class DirectLanTransport(
    context: Context,
    private val localPeer: DiscoveredPeer,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PeerTransport, ManualPeerTransport {
    override val modeId: String = "lan-nsd-tcp"

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val running = AtomicBoolean(false)
    private val peers = Collections.synchronizedMap(linkedMapOf<String, DirectLanPeer>())
    private val incoming = Collections.synchronizedList(mutableListOf<ReceivedPacket>())
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile
    private var diagnostics = MeshTransportDiagnostics()

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        val socket = ServerSocket(0).also { it.soTimeout = ACCEPT_TIMEOUT_MS }
        serverSocket = socket
        val multicastHeld = acquireMulticastLock()
        updateDiagnostics {
            it.copy(
                startedAtEpochMillis = clock(),
                localPort = socket.localPort,
                localAddresses = localIpv4Addresses(),
                multicastLockHeld = multicastHeld,
                registrationState = "starting",
                discoveryState = "starting",
                lastError = if (multicastHeld) null else it.lastError,
            )
        }
        serverThread = Thread({ acceptLoop(socket) }, "kraken-lan-accept").also { it.start() }
        registerService(socket.localPort)
        discoverPeers()
    }

    override fun stop() {
        running.set(false)
        runCatching { registrationListener?.let(nsdManager::unregisterService) }
        runCatching { discoveryListener?.let(nsdManager::stopServiceDiscovery) }
        runCatching { serverSocket?.close() }
        releaseMulticastLock()
        serverThread = null
        serverSocket = null
        registrationListener = null
        discoveryListener = null
        synchronized(peers) { peers.clear() }
        updateDiagnostics {
            it.copy(
                registrationState = "stopped",
                discoveryState = "stopped",
                multicastLockHeld = false,
                discoveredPeerCount = 0,
            )
        }
    }

    override fun observePeers(): List<DiscoveredPeer> {
        val observed = synchronized(peers) { peers.values.map { it.peer } }
        updateDiagnostics { it.copy(discoveredPeerCount = observed.size, manualPeerCount = manualPeerCount()) }
        return observed
    }

    override fun addManualPeer(
        fingerprint: String,
        host: String,
        port: Int,
        displayName: String?,
    ): TransportSendResult {
        val cleanFingerprint = fingerprint.trim()
        val cleanHost = host.trim()
        if (cleanFingerprint.isBlank()) {
            return manualPeerFailed("missing-fingerprint")
        }
        if (cleanFingerprint == localPeer.fingerprint) {
            return manualPeerFailed("self-fingerprint")
        }
        if (cleanHost.isBlank()) {
            return manualPeerFailed("missing-host")
        }
        if (port !in 1..65535) {
            return manualPeerFailed("invalid-port")
        }
        return runCatching {
            val address = InetAddress.getByName(cleanHost)
            val peerId = "manual-${address.hostAddress}:$port-${cleanFingerprint.filter { it.isLetterOrDigit() }.take(12)}"
            upsertPeer(
                peerId,
                DirectLanPeer(
                    peer = DiscoveredPeer(
                        peerId = peerId,
                        fingerprint = cleanFingerprint,
                        displayName = displayName?.trim()?.takeIf { it.isNotBlank() },
                    ),
                    host = address,
                    port = port,
                    serviceName = "manual-$peerId",
                ),
            )
            updateDiagnostics {
                it.copy(
                    discoveredPeerCount = peerCount(),
                    manualPeerCount = manualPeerCount(),
                    lastError = null,
                )
            }
            TransportSendResult(true)
        }.getOrElse { error ->
            manualPeerFailed(error.message ?: "invalid-host")
        }
    }

    override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult {
        val candidates = synchronized(peers) {
            buildList {
                peers[peer.peerId]?.let(::add)
                peers.values
                    .filter { it.peer.fingerprint == peer.fingerprint && it.peer.peerId != peer.peerId }
                    .asReversed()
                    .forEach(::add)
            }
        }
        if (candidates.isEmpty()) {
            return TransportSendResult(false, "peer-not-found").also {
                recordSendFailure("peer-not-found:${peer.fingerprint}")
            }
        }
        var lastFailure = "lan-send-failed"
        candidates.forEach { lanPeer ->
            val result = sendToLanPeer(lanPeer, packet)
            if (result.success) return result
            lastFailure = result.error ?: lastFailure
            removePeersMatching {
                it.peer.fingerprint == lanPeer.peer.fingerprint &&
                    it.host == lanPeer.host &&
                    it.port == lanPeer.port
            }
        }
        return TransportSendResult(false, lastFailure)
    }

    private fun sendToLanPeer(lanPeer: DirectLanPeer, packet: KrakenPacket): TransportSendResult =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(lanPeer.host, lanPeer.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                socket.getOutputStream().write(
                    LanFrameCodec.encode(
                        packet = packet,
                        senderPeer = localPeer,
                        senderReplyPort = serverSocket?.localPort,
                    ),
                )
                socket.getOutputStream().flush()
                LanFrameCodec.readAck(socket.getInputStream())
            }
            TransportSendResult(true)
        }.getOrElse { error ->
            val reason = error.message ?: "lan-send-failed"
            recordSendFailure(reason)
            TransportSendResult(false, reason)
        }

    override fun observePackets(): List<ReceivedPacket> {
        synchronized(incoming) {
            val packets = incoming.toList()
            incoming.clear()
            return packets
        }
    }

    override fun diagnostics(): MeshTransportDiagnostics {
        val observed = observePeers()
        return diagnostics.copy(
            transportModes = listOf(modeId),
            discoveredPeerCount = observed.size,
            manualPeerCount = manualPeerCount(),
            localAddresses = diagnostics.localAddresses.ifEmpty { localIpv4Addresses() },
            multicastLockHeld = multicastLock?.isHeld == true,
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

    private fun acquireMulticastLock(): Boolean {
        val current = multicastLock
        if (current?.isHeld == true) return true
        return runCatching {
            val lock = current ?: wifiManager?.createMulticastLock(MULTICAST_LOCK_TAG)?.also {
                it.setReferenceCounted(false)
                multicastLock = it
            }
            lock?.acquire()
            lock?.isHeld == true
        }.getOrElse { error ->
            updateDiagnostics {
                it.copy(lastError = "multicast-lock:${error.message ?: error::class.java.simpleName}")
            }
            false
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            multicastLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            try {
                socket.accept().use { client ->
                    client.soTimeout = READ_TIMEOUT_MS
                    val envelope = LanFrameCodec.decodeEnvelope(client.getInputStream())
                    val packet = envelope.packet
                    val replyPort = envelope.senderReplyPort
                    if (replyPort != null && replyPort > 0) {
                        upsertPeer(
                            envelope.senderPeerId,
                            DirectLanPeer(
                                peer = DiscoveredPeer(
                                    peerId = envelope.senderPeerId,
                                    fingerprint = envelope.senderFingerprint,
                                    displayName = envelope.senderDisplayName,
                                ),
                                host = client.inetAddress,
                                port = replyPort,
                                serviceName = "inbound-${envelope.senderFingerprint}",
                            ),
                        )
                    }
                    incoming += ReceivedPacket(
                        fromPeer = DiscoveredPeer(
                            peerId = envelope.senderPeerId,
                            fingerprint = envelope.senderFingerprint,
                            displayName = envelope.senderDisplayName,
                        ),
                        packet = packet,
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
                // Poll running flag.
            } catch (error: Exception) {
                // Malformed frames are dropped by design; metrics are recorded by the inbox layer.
                updateDiagnostics {
                    it.copy(
                        malformedFramesDropped = it.malformedFramesDropped + 1,
                        lastError = "accept:${error.message ?: error::class.java.simpleName}",
                    )
                }
            }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = serviceNameFor(localPeer)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute(ATTR_FINGERPRINT, localPeer.fingerprint)
            setAttribute(ATTR_DISPLAY_NAME, localPeer.displayName.orEmpty())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                updateDiagnostics { it.copy(registrationState = "registered:${serviceInfo.serviceName}", lastError = null) }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                updateDiagnostics {
                    it.copy(
                        registrationState = "failed:$errorCode",
                        lastError = "registration-failed:$errorCode",
                    )
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                updateDiagnostics { it.copy(registrationState = "unregistered:${serviceInfo.serviceName}") }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                updateDiagnostics {
                    it.copy(
                        registrationState = "unregister-failed:$errorCode",
                        lastError = "unregister-failed:$errorCode",
                    )
                }
            }
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun discoverPeers() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                updateDiagnostics { it.copy(discoveryState = "started:$serviceType", lastError = null) }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                updateDiagnostics { it.copy(discoveryState = "stopped:$serviceType") }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                updateDiagnostics {
                    it.copy(
                        discoveryState = "start-failed:$errorCode",
                        lastError = "discovery-start-failed:$errorCode",
                    )
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                updateDiagnostics {
                    it.copy(
                        discoveryState = "stop-failed:$errorCode",
                        lastError = "discovery-stop-failed:$errorCode",
                    )
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                removePeersMatching { it.serviceName == serviceInfo.serviceName }
                updateDiagnostics { it.copy(discoveredPeerCount = peerCount()) }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE || serviceInfo.serviceName == serviceNameFor(localPeer)) return
                nsdManager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            updateDiagnostics {
                                it.copy(
                                    lastError = "resolve-failed:${serviceInfo.serviceName}:$errorCode",
                                )
                            }
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host ?: return
                            val fingerprint = resolved.attributeString(ATTR_FINGERPRINT)
                            if (fingerprint.isNullOrBlank() || fingerprint == localPeer.fingerprint) return
                            val peerId = "${host.hostAddress}:${resolved.port}"
                            upsertPeer(
                                peerId,
                                DirectLanPeer(
                                    peer = DiscoveredPeer(
                                        peerId = peerId,
                                        fingerprint = fingerprint,
                                        displayName = resolved.attributeString(ATTR_DISPLAY_NAME),
                                    ),
                                    host = host,
                                    port = resolved.port,
                                    serviceName = resolved.serviceName,
                                ),
                            )
                            updateDiagnostics { it.copy(discoveredPeerCount = peerCount(), lastError = null) }
                        }
                    },
                )
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun NsdServiceInfo.attributeString(name: String): String? =
        attributes[name]?.decodeToString()

    private fun serviceNameFor(peer: DiscoveredPeer): String =
        "Kraken-${peer.fingerprint.filter { it.isLetterOrDigit() }.take(24)}"

    private fun recordSendFailure(reason: String) {
        updateDiagnostics {
            it.copy(
                sendFailures = it.sendFailures + 1,
                lastError = "send:$reason",
            )
        }
    }

    private fun manualPeerFailed(reason: String): TransportSendResult {
        updateDiagnostics { it.copy(lastError = "manual-peer:$reason") }
        return TransportSendResult(false, reason)
    }

    private fun manualPeerCount(): Int =
        synchronized(peers) { peers.values.count { it.serviceName.startsWith("manual-") } }

    private fun peerCount(): Int =
        synchronized(peers) { peers.size }

    private fun upsertPeer(peerId: String, peer: DirectLanPeer) {
        synchronized(peers) {
            peers.entries.removeAll {
                it.key != peerId &&
                    (
                        it.value.peer.fingerprint == peer.peer.fingerprint ||
                            it.value.serviceName == peer.serviceName
                        )
            }
            peers[peerId] = peer
        }
    }

    private fun removePeersMatching(predicate: (DirectLanPeer) -> Boolean) {
        synchronized(peers) { peers.values.removeAll(predicate) }
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

    private fun updateDiagnostics(transform: (MeshTransportDiagnostics) -> MeshTransportDiagnostics) {
        diagnostics = transform(diagnostics)
    }

    data class DirectLanPeer(
        val peer: DiscoveredPeer,
        val host: InetAddress,
        val port: Int,
        val serviceName: String,
    )

    companion object {
        const val SERVICE_TYPE = "_kraken._tcp."
        private const val ATTR_FINGERPRINT = "fingerprint"
        private const val ATTR_DISPLAY_NAME = "display"
        private const val ACCEPT_TIMEOUT_MS = 1_000
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val MULTICAST_LOCK_TAG = "kraken-lan-nsd"
    }
}
