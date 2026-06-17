package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleFrameCodecTest {
    @Test
    fun chunksRoundTripIntoLanEnvelope() {
        val sender = DiscoveredPeer("peer-alice", "ALICE-FP", "Alice")
        val packet = packet(payloadJson = "hello".repeat(80))
        val chunks = BleFrameCodec.encodeChunks(packet, sender, chunkPayloadBytes = 64)
        val reassembler = BleFrameReassembler(clock = { 1_000 })
        var envelope: LanFrameEnvelope? = null

        chunks.forEachIndexed { index, bytes ->
            val result = reassembler.accept(BleFrameCodec.decodeChunk(bytes)).getOrThrow()
            if (index < chunks.lastIndex) {
                assertNull(result)
            }
            envelope = result ?: envelope
        }

        assertEquals(packet, envelope?.packet)
        assertEquals(sender.fingerprint, envelope?.senderFingerprint)
        assertEquals(sender.displayName, envelope?.senderDisplayName)
    }

    @Test
    fun defaultChunksFitGattWritePayload() {
        val sender = DiscoveredPeer(
            peerId = "peer-${"a".repeat(48)}",
            fingerprint = "ALICE-FINGERPRINT-${"b".repeat(48)}",
            displayName = "Alice ${"c".repeat(32)}",
        )
        val chunks = BleFrameCodec.encodeChunks(packet(payloadJson = "hello".repeat(120)), sender)

        assertTrue(chunks.isNotEmpty())
        assertTrue(
            "Encoded BLE chunk exceeded ${BleFrameCodec.MAX_GATT_WRITE_BYTES} bytes: ${chunks.maxOf { it.size }}",
            chunks.all { it.size <= BleFrameCodec.MAX_GATT_WRITE_BYTES },
        )
    }

    @Test
    fun rejectsOversizedPacket() {
        val sender = DiscoveredPeer("peer-alice", "ALICE-FP", "Alice")
        val packet = packet(payloadJson = "x".repeat(BleFrameCodec.MAX_PACKET_BYTES + 1))

        val failure = runCatching { BleFrameCodec.encodeChunks(packet, sender) }

        assertTrue(failure.isFailure)
    }

    @Test
    fun rejectsExpiredPartialTransfer() {
        var now = 1_000L
        val sender = DiscoveredPeer("peer-alice", "ALICE-FP", "Alice")
        val chunks = BleFrameCodec.encodeChunks(packet(payloadJson = "hello".repeat(80)), sender, chunkPayloadBytes = 64)
        val reassembler = BleFrameReassembler(clock = { now }, transferTtlMillis = 10)

        reassembler.accept(BleFrameCodec.decodeChunk(chunks.first())).getOrThrow()
        now += 20
        val result = reassembler.accept(BleFrameCodec.decodeChunk(chunks.last())).getOrThrow()

        assertNull(result)
    }

    private fun packet(payloadJson: String): KrakenPacket =
        KrakenPacket(
            packetId = "packet-1",
            packetType = KrakenPacketType.MESSAGE,
            senderFingerprint = "ALICE-FP",
            recipientFingerprint = "BOB-FP",
            relationshipId = "relationship-1",
            conversationId = "conversation-1",
            messageId = "message-1",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 3,
            payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
            payloadJson = payloadJson,
        )
}
