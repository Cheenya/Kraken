package com.disser.kraken.mesh

import com.disser.kraken.invite.InvitePayloadCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LanFrameCodecTest {
    @Test
    fun lengthPrefixedFrameRoundTripsPacket() {
        val packet = packet()

        val decoded = LanFrameCodec.decode(ByteArrayInputStream(LanFrameCodec.encode(packet)))

        assertEquals(packet, decoded)
    }

    @Test
    fun frameEnvelopeCarriesReplyPeerHint() {
        val packet = packet()
        val peer = DiscoveredPeer(
            peerId = "local-alice",
            fingerprint = "ALICE-FP",
            displayName = "Alice",
        )

        val decoded = LanFrameCodec.decodeEnvelope(
            ByteArrayInputStream(
                LanFrameCodec.encode(packet, senderPeer = peer, senderReplyPort = 42001),
            ),
        )

        assertEquals(1, decoded.frameVersion)
        assertEquals("local-alice", decoded.senderPeerId)
        assertEquals("ALICE-FP", decoded.senderFingerprint)
        assertEquals("Alice", decoded.senderDisplayName)
        assertEquals(42001, decoded.senderReplyPort)
        assertEquals(packet, decoded.packet)
    }

    @Test
    fun legacyPacketFrameDecodesAsEnvelopeWithoutReplyPort() {
        val packet = packet()

        val decoded = LanFrameCodec.decodeEnvelope(ByteArrayInputStream(LanFrameCodec.encode(packet)))

        assertEquals("lan-ALICE-FP", decoded.senderPeerId)
        assertEquals("ALICE-FP", decoded.senderFingerprint)
        assertEquals(null, decoded.senderReplyPort)
        assertEquals(packet, decoded.packet)
    }

    @Test
    fun envelopeSenderMustMatchPacketSender() {
        val mismatchedEnvelope = LanFrameEnvelope(
            senderPeerId = "spoofed-peer",
            senderFingerprint = "MALLORY-FP",
            packet = packet(senderFingerprint = "ALICE-FP"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            LanFrameCodec.decodeEnvelope(ByteArrayInputStream(frame(mismatchedEnvelope)))
        }
    }

    @Test
    fun oversizedFrameRejectedBeforeSend() {
        val payload = "x".repeat(LanFrameCodec.MAX_FRAME_BYTES + 1)

        assertThrows(IllegalArgumentException::class.java) {
            LanFrameCodec.encode(packet(payloadJson = payload))
        }
    }

    @Test
    fun malformedLengthRejected() {
        val invalid = byteArrayOf(0x00, 0x05, 0x00, 0x00)

        assertThrows(IllegalArgumentException::class.java) {
            LanFrameCodec.decode(ByteArrayInputStream(invalid))
        }
    }

    @Test
    fun frameAckRoundTripsAfterAcceptedPacket() {
        val output = ByteArrayOutputStream()

        LanFrameCodec.writeAck(output)

        LanFrameCodec.readAck(ByteArrayInputStream(output.toByteArray()))
    }

    @Test
    fun frameAckIsRequiredForSendSuccess() {
        assertThrows(IllegalArgumentException::class.java) {
            LanFrameCodec.readAck(ByteArrayInputStream(byteArrayOf()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LanFrameCodec.readAck(ByteArrayInputStream(byteArrayOf(0x15)))
        }
    }

    @Test
    fun macosCompactEnvelopeFrameDecodesOnAndroid() {
        val decoded = LanFrameCodec.decodeEnvelope(
            ByteArrayInputStream(MACOS_COMPACT_ENVELOPE_FRAME_HEX.hexToBytes()),
        )

        assertEquals(1, decoded.frameVersion)
        assertEquals("macos-fixture-peer", decoded.senderPeerId)
        assertEquals("MACOS-FP", decoded.senderFingerprint)
        assertEquals("Kraken Desktop", decoded.senderDisplayName)
        assertEquals(43191, decoded.senderReplyPort)
        assertEquals("packet-fixture", decoded.packet.packetId)
        assertEquals(KrakenPacketType.MESSAGE, decoded.packet.packetType)
        assertEquals(PacketPayloadType.LOCAL_MESSAGE_JSON, decoded.packet.payloadType)
        assertEquals("ANDROID-FP", decoded.packet.recipientFingerprint)
        assertEquals("""{"body":"hello from macOS","message_id":"message-fixture"}""", decoded.packet.payloadJson)
    }

    private fun packet(
        payloadJson: String = "{}",
        senderFingerprint: String = "ALICE-FP",
    ): KrakenPacket =
        KrakenPacket(
            packetId = "packet-lan",
            packetType = KrakenPacketType.PING,
            senderFingerprint = senderFingerprint,
            recipientFingerprint = "BOB-FP",
            relationshipId = "relationship-1",
            conversationId = "conversation-1",
            messageId = null,
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 4,
            payloadType = PacketPayloadType.PING_JSON,
            payloadJson = payloadJson,
        )

    private fun frame(envelope: LanFrameEnvelope): ByteArray {
        val payload = InvitePayloadCodec.json.encodeToString(envelope).encodeToByteArray()
        val output = ByteArrayOutputStream(4 + payload.size)
        output.write(ByteBuffer.allocate(4).putInt(payload.size).array())
        output.write(payload)
        return output.toByteArray()
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private companion object {
        private const val MACOS_COMPACT_ENVELOPE_FRAME_HEX =
            "0000035f7b226672616d655f76657273696f6e223a312c2273656e6465725f706565725f6964223a226d61636f732d666978747572652d70656572222c2273656e6465725f66696e6765727072696e74223a224d41434f532d4650222c2273656e6465725f646973706c61795f6e616d65223a224b72616b656e204465736b746f70222c2273656e6465725f7265706c795f706f7274223a34333139312c227061636b6574223a7b227061636b65745f6964223a227061636b65742d66697874757265222c2270726f746f636f6c5f76657273696f6e223a312c227061636b65745f74797065223a224d455353414745222c2273656e6465725f66696e6765727072696e74223a224d41434f532d4650222c22726563697069656e745f66696e6765727072696e74223a22414e44524f49442d4650222c2272656c6174696f6e736869705f6964223a2272656c6174696f6e736869702d66697874757265222c22636f6e766572736174696f6e5f6964223a22636f6e766572736174696f6e2d66697874757265222c226d6573736167655f6964223a226d6573736167652d66697874757265222c22637265617465645f61745f65706f63685f6d696c6c6973223a313730303030303030303030302c22657870697265735f61745f65706f63685f6d696c6c6973223a313730303030303330303030302c2274746c5f686f7073223a342c227061796c6f61645f74797065223a224c4f43414c5f4d4553534147455f4a534f4e222c227061796c6f61645f6a736f6e223a227b5c22626f64795c223a5c2268656c6c6f2066726f6d206d61634f535c222c5c226d6573736167655f69645c223a5c226d6573736167652d666978747572655c227d222c2263727970746f5f70726f66696c655f6964223a227374616e646172642d72657669657765642d7072696d6974697665732d7631222c2273657373696f6e5f70726f66696c655f6964223a6e756c6c2c2261646d697373696f6e5f6465636973696f6e5f68617368223a227368613235363a7374616e646172642d72657669657765642d7072696d6974697665732d76313a6e6f742d6170706c696361626c653a7631222c2270726f66696c655f706f6c6963795f76657273696f6e223a312c2270726f6f665f6d6f6465223a2270726f746f747970652d706c616365686f6c646572227d7d"
    }
}
