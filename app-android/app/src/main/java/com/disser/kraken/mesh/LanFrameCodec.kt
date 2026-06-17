package com.disser.kraken.mesh

import com.disser.kraken.invite.InvitePayloadCodec
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class LanFrameEnvelope(
    @SerialName("frame_version")
    val frameVersion: Int = 1,
    @SerialName("sender_peer_id")
    val senderPeerId: String,
    @SerialName("sender_fingerprint")
    val senderFingerprint: String,
    @SerialName("sender_display_name")
    val senderDisplayName: String? = null,
    @SerialName("sender_reply_port")
    val senderReplyPort: Int? = null,
    val packet: KrakenPacket,
)

object LanFrameCodec {
    const val MAX_FRAME_BYTES = 256 * 1024
    private const val ACK_BYTE = 0x06
    private const val LENGTH_PREFIX_BYTES = 4

    fun encode(
        packet: KrakenPacket,
        senderPeer: DiscoveredPeer? = null,
        senderReplyPort: Int? = null,
    ): ByteArray {
        val encoded = if (senderPeer == null) {
            MeshPacketCodec.encode(packet)
        } else {
            InvitePayloadCodec.json.encodeToString(
                LanFrameEnvelope(
                    senderPeerId = senderPeer.peerId,
                    senderFingerprint = senderPeer.fingerprint,
                    senderDisplayName = senderPeer.displayName,
                    senderReplyPort = senderReplyPort,
                    packet = packet,
                ),
            )
        }
        val payload = encoded.encodeToByteArray()
        require(payload.size <= MAX_FRAME_BYTES) { "Kraken packet frame exceeds $MAX_FRAME_BYTES bytes." }
        val output = ByteArrayOutputStream(LENGTH_PREFIX_BYTES + payload.size)
        output.write(ByteBuffer.allocate(LENGTH_PREFIX_BYTES).putInt(payload.size).array())
        output.write(payload)
        return output.toByteArray()
    }

    fun decode(input: InputStream): KrakenPacket {
        val lengthBytes = input.readFully(LENGTH_PREFIX_BYTES)
        val length = ByteBuffer.wrap(lengthBytes).int
        require(length in 1..MAX_FRAME_BYTES) { "Invalid Kraken packet frame length." }
        val payload = input.readFully(length).decodeToString()
        return MeshPacketCodec.decode(payload).getOrElse {
            throw IllegalArgumentException("Malformed Kraken packet frame.", it)
        }
    }

    fun decodeEnvelope(input: InputStream): LanFrameEnvelope {
        val payload = readPayload(input)
        return runCatching {
            InvitePayloadCodec.json.decodeFromString<LanFrameEnvelope>(payload)
                .validated()
        }.getOrElse {
            val packet = MeshPacketCodec.decode(payload).getOrElse { error ->
                throw IllegalArgumentException("Malformed Kraken packet frame.", error)
            }
            LanFrameEnvelope(
                senderPeerId = "lan-${packet.senderFingerprint}",
                senderFingerprint = packet.senderFingerprint,
                packet = packet,
            )
        }
    }

    fun writeAck(output: OutputStream) {
        output.write(ACK_BYTE)
        output.flush()
    }

    fun readAck(input: InputStream) {
        val ack = input.read()
        require(ack == ACK_BYTE) { "Kraken packet frame ACK was not received." }
    }

    private fun LanFrameEnvelope.validated(): LanFrameEnvelope {
        require(senderFingerprint == packet.senderFingerprint) {
            "LAN frame sender fingerprint does not match packet sender."
        }
        return this
    }

    private fun readPayload(input: InputStream): String {
        val lengthBytes = input.readFully(LENGTH_PREFIX_BYTES)
        val length = ByteBuffer.wrap(lengthBytes).int
        require(length in 1..MAX_FRAME_BYTES) { "Invalid Kraken packet frame length." }
        return input.readFully(length).decodeToString()
    }

    private fun InputStream.readFully(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(buffer, offset, length - offset)
            if (read == -1) {
                throw IllegalArgumentException("Unexpected end of Kraken packet frame.")
            }
            offset += read
        }
        return buffer
    }
}
