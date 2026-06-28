package com.disser.kraken.mesh

class InMemoryTwoNodeTransport(
    private val localPeer: DiscoveredPeer,
    private val bus: SharedBus,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PeerTransport {
    override val modeId: String = "in-memory"

    private var running = false

    override fun start() {
        running = true
        bus.register(localPeer)
    }

    override fun stop() {
        running = false
    }

    override fun observePeers(): List<DiscoveredPeer> =
        if (running) bus.peers().filterNot { it.fingerprint == localPeer.fingerprint } else emptyList()

    override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult {
        if (!running) {
            return TransportSendResult(false, "transport-stopped")
        }
        return if (bus.deliver(localPeer, peer, packet, clock())) {
            TransportSendResult(true)
        } else {
            TransportSendResult(false, "peer-not-found")
        }
    }

    override fun observePackets(): List<ReceivedPacket> =
        if (running) bus.drain(localPeer.fingerprint) else emptyList()

    class SharedBus {
        private val peers = linkedMapOf<String, DiscoveredPeer>()
        private val queues = linkedMapOf<String, MutableList<ReceivedPacket>>()

        fun register(peer: DiscoveredPeer) {
            peers[peer.fingerprint] = peer
            queues.getOrPut(peer.fingerprint) { mutableListOf() }
        }

        fun peers(): List<DiscoveredPeer> =
            peers.values.toList()

        fun deliver(
            fromPeer: DiscoveredPeer,
            toPeer: DiscoveredPeer,
            packet: KrakenPacket,
            nowEpochMillis: Long,
        ): Boolean {
            val queue = queues[toPeer.fingerprint] ?: return false
            queue += ReceivedPacket(fromPeer, packet, nowEpochMillis)
            return true
        }

        fun drain(fingerprint: String): List<ReceivedPacket> {
            val queue = queues[fingerprint] ?: return emptyList()
            val packets = queue.toList()
            queue.clear()
            return packets
        }
    }
}

class LoopbackTransport(
    localPeer: DiscoveredPeer,
    clock: () -> Long = { System.currentTimeMillis() },
) : PeerTransport {
    override val modeId: String = "loopback-local"

    private val bus = InMemoryTwoNodeTransport.SharedBus()
    private val delegate = InMemoryTwoNodeTransport(localPeer, bus, clock)

    override fun start() = delegate.start()

    override fun stop() = delegate.stop()

    override fun observePeers(): List<DiscoveredPeer> = delegate.observePeers()

    override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult =
        delegate.send(peer, packet)

    override fun observePackets(): List<ReceivedPacket> = delegate.observePackets()
}
