package com.disser.kraken.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LanTransportModelsTest {
    @Test
    fun manualPeerRejectsInvalidPort() {
        assertThrows(IllegalArgumentException::class.java) {
            ManualLanPeer(peerId = "peer-a", host = "192.168.1.10", port = 0)
        }
    }

    @Test
    fun summaryExchangeComputesMissingPacketSets() {
        val summary = SummaryExchange(
            sessionId = "session-1",
            localPacketIds = setOf("a", "b"),
            remotePacketIds = setOf("b", "c"),
        )

        assertEquals(setOf("a"), summary.missingOnRemote)
        assertEquals(setOf("c"), summary.missingLocally)
    }

    @Test
    fun debugAdapterSessionLifecycleDoesNotOpenRealSockets() {
        val adapter = DebugLanTransportAdapter()
        val peer = ManualLanPeer(peerId = "peer-a", host = "192.168.1.10", port = 4040)

        val started = adapter.start()
        val session = adapter.openSession(peer)
        val sent = adapter.sendPacket(
            PacketSendRequest(
                sessionId = session.sessionId,
                packetId = "packet-1",
                encryptedPayload = "ciphertext".encodeToByteArray(),
            )
        )
        val closed = adapter.closeSession(session)
        val stopped = adapter.stop()

        assertTrue(started.running)
        assertEquals(TransportSessionState.OPEN, session.state)
        assertTrue(sent)
        assertEquals(TransportSessionState.CLOSED, closed.state)
        assertFalse(stopped.running)
        assertTrue(started.note.contains("No sockets"))
    }

    @Test
    fun debugAdapterRequiresStartBeforeOpenSession() {
        val adapter = DebugLanTransportAdapter()

        assertThrows(IllegalArgumentException::class.java) {
            adapter.openSession(ManualLanPeer(peerId = "peer-a", host = "192.168.1.10", port = 4040))
        }
    }
}
