package com.disser.kraken.transport

data class ManualLanPeer(
    val peerId: String,
    val host: String,
    val port: Int,
) {
    init {
        require(port in 1..65_535) { "Port must be in 1..65535" }
    }
}

data class TransportSession(
    val sessionId: String,
    val peer: ManualLanPeer,
    val state: TransportSessionState,
)

enum class TransportSessionState {
    CREATED,
    OPEN,
    CLOSED,
    FAILED,
}

data class SummaryExchange(
    val sessionId: String,
    val localPacketIds: Set<String>,
    val remotePacketIds: Set<String>,
) {
    val missingOnRemote: Set<String>
        get() = localPacketIds - remotePacketIds

    val missingLocally: Set<String>
        get() = remotePacketIds - localPacketIds
}

data class PacketSendRequest(
    val sessionId: String,
    val packetId: String,
    val encryptedPayload: ByteArray,
)

data class TransportStatus(
    val running: Boolean,
    val sessions: List<TransportSession>,
    val note: String,
)

interface TransportAdapter {
    fun start(): TransportStatus
    fun stop(): TransportStatus
    fun manualPeer(peer: ManualLanPeer): ManualLanPeer
    fun openSession(peer: ManualLanPeer): TransportSession
    fun exchangeSummary(session: TransportSession, localPacketIds: Set<String>): SummaryExchange
    fun sendPacket(request: PacketSendRequest): Boolean
    fun closeSession(session: TransportSession): TransportSession
}

class DebugLanTransportAdapter : TransportAdapter {
    private var running = false
    private val sessions = mutableListOf<TransportSession>()

    override fun start(): TransportStatus {
        running = true
        return status("Debug LAN transport abstraction started. No sockets are opened in this phase.")
    }

    override fun stop(): TransportStatus {
        running = false
        sessions.clear()
        return status("Debug LAN transport abstraction stopped.")
    }

    override fun manualPeer(peer: ManualLanPeer): ManualLanPeer = peer

    override fun openSession(peer: ManualLanPeer): TransportSession {
        require(running) { "Transport must be started before opening sessions" }
        val session = TransportSession(
            sessionId = "lan-${peer.peerId}-${sessions.size + 1}",
            peer = peer,
            state = TransportSessionState.OPEN,
        )
        sessions += session
        return session
    }

    override fun exchangeSummary(
        session: TransportSession,
        localPacketIds: Set<String>,
    ): SummaryExchange {
        require(session.state == TransportSessionState.OPEN) { "Session must be open" }
        return SummaryExchange(
            sessionId = session.sessionId,
            localPacketIds = localPacketIds,
            remotePacketIds = emptySet(),
        )
    }

    override fun sendPacket(request: PacketSendRequest): Boolean {
        require(running) { "Transport must be running" }
        return sessions.any { it.sessionId == request.sessionId && it.state == TransportSessionState.OPEN }
    }

    override fun closeSession(session: TransportSession): TransportSession {
        val closed = session.copy(state = TransportSessionState.CLOSED)
        sessions.replaceAll { current -> if (current.sessionId == session.sessionId) closed else current }
        return closed
    }

    private fun status(note: String): TransportStatus =
        TransportStatus(
            running = running,
            sessions = sessions.toList(),
            note = note,
        )
}
