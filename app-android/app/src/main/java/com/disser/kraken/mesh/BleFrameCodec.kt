package com.disser.kraken.mesh

import com.disser.kraken.invite.InvitePayloadCodec
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class BleFrameChunk(
    @SerialName("frame_version")
    val frameVersion: Int = 1,
    @SerialName("transfer_id")
    val transferId: String,
    @SerialName("sender_peer_id")
    val senderPeerId: String,
    @SerialName("sender_fingerprint")
    val senderFingerprint: String,
    @SerialName("sender_display_name")
    val senderDisplayName: String? = null,
    @SerialName("chunk_index")
    val chunkIndex: Int,
    @SerialName("chunk_count")
    val chunkCount: Int,
    @SerialName("payload_size")
    val payloadSize: Int,
    @SerialName("payload_crc32")
    val payloadCrc32: Long,
    @SerialName("payload_base64")
    val payloadBase64: String,
)

object BleFrameCodec {
    const val MAX_PACKET_BYTES = 32 * 1024
    const val DEFAULT_CHUNK_PAYLOAD_BYTES = 24
    const val MAX_GATT_WRITE_BYTES = 512

    fun encodeChunks(
        packet: KrakenPacket,
        senderPeer: DiscoveredPeer,
        chunkPayloadBytes: Int = DEFAULT_CHUNK_PAYLOAD_BYTES,
    ): List<ByteArray> {
        require(chunkPayloadBytes in 1..1024) { "Invalid BLE chunk payload size." }
        val payload = MeshPacketCodec.encode(packet).encodeToByteArray()
        require(payload.size in 1..MAX_PACKET_BYTES) { "Kraken BLE packet exceeds $MAX_PACKET_BYTES bytes." }
        val transferId = "${packet.packetId}-${UUID.randomUUID()}"
        val crc32 = payload.crc32()
        val chunkCount = (payload.size + chunkPayloadBytes - 1) / chunkPayloadBytes
        return (0 until chunkCount).map { index ->
            val start = index * chunkPayloadBytes
            val end = minOf(start + chunkPayloadBytes, payload.size)
            val chunk = BleFrameChunk(
                transferId = transferId,
                senderPeerId = senderPeer.peerId,
                senderFingerprint = senderPeer.fingerprint,
                senderDisplayName = senderPeer.displayName,
                chunkIndex = index,
                chunkCount = chunkCount,
                payloadSize = payload.size,
                payloadCrc32 = crc32,
                payloadBase64 = Base64.getEncoder().encodeToString(payload.copyOfRange(start, end)),
            )
            InvitePayloadCodec.json.encodeToString(chunk).encodeToByteArray()
        }
    }

    fun decodeChunk(bytes: ByteArray): BleFrameChunk =
        InvitePayloadCodec.json.decodeFromString<BleFrameChunk>(bytes.decodeToString()).validated()

    private fun BleFrameChunk.validated(): BleFrameChunk {
        require(frameVersion == 1) { "Unsupported BLE frame version." }
        require(transferId.isNotBlank()) { "Missing BLE transfer id." }
        require(senderFingerprint.isNotBlank()) { "Missing BLE sender fingerprint." }
        require(chunkCount in 1..512) { "Invalid BLE chunk count." }
        require(chunkIndex in 0 until chunkCount) { "Invalid BLE chunk index." }
        require(payloadSize in 1..MAX_PACKET_BYTES) { "Invalid BLE payload size." }
        Base64.getDecoder().decode(payloadBase64)
        return this
    }

}

private fun ByteArray.crc32(): Long =
    CRC32().also { it.update(this) }.value

class BleFrameReassembler(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val transferTtlMillis: Long = 60_000,
) {
    private val pending = linkedMapOf<String, PendingTransfer>()

    fun accept(chunk: BleFrameChunk): Result<LanFrameEnvelope?> =
        runCatching {
            pruneExpired()
            val transfer = pending.getOrPut(chunk.transferId) {
                PendingTransfer(
                    firstSeenAtEpochMillis = clock(),
                    senderPeerId = chunk.senderPeerId,
                    senderFingerprint = chunk.senderFingerprint,
                    senderDisplayName = chunk.senderDisplayName,
                    chunkCount = chunk.chunkCount,
                    payloadSize = chunk.payloadSize,
                    payloadCrc32 = chunk.payloadCrc32,
                )
            }
            require(transfer.matches(chunk)) { "BLE transfer metadata mismatch." }
            transfer.chunks[chunk.chunkIndex] = Base64.getDecoder().decode(chunk.payloadBase64)
            if (transfer.chunks.size != transfer.chunkCount) {
                return@runCatching null
            }
            pending.remove(chunk.transferId)
            val payload = ByteArray(transfer.payloadSize)
            var offset = 0
            (0 until transfer.chunkCount).forEach { index ->
                val bytes = requireNotNull(transfer.chunks[index]) { "Missing BLE chunk." }
                require(offset + bytes.size <= payload.size) { "BLE payload exceeds expected size." }
                bytes.copyInto(payload, destinationOffset = offset)
                offset += bytes.size
            }
            require(offset == payload.size) { "BLE payload size mismatch." }
            require(payload.crc32() == transfer.payloadCrc32) { "BLE payload checksum mismatch." }
            val packet = MeshPacketCodec.decode(payload.decodeToString()).getOrElse {
                throw IllegalArgumentException("Malformed BLE packet.", it)
            }
            require(packet.senderFingerprint == transfer.senderFingerprint) {
                "BLE sender fingerprint does not match packet sender."
            }
            LanFrameEnvelope(
                senderPeerId = transfer.senderPeerId,
                senderFingerprint = transfer.senderFingerprint,
                senderDisplayName = transfer.senderDisplayName,
                packet = packet,
            )
        }

    private fun pruneExpired() {
        val now = clock()
        pending.entries.removeIf { (_, transfer) ->
            now - transfer.firstSeenAtEpochMillis > transferTtlMillis
        }
    }

    private data class PendingTransfer(
        val firstSeenAtEpochMillis: Long,
        val senderPeerId: String,
        val senderFingerprint: String,
        val senderDisplayName: String?,
        val chunkCount: Int,
        val payloadSize: Int,
        val payloadCrc32: Long,
        val chunks: MutableMap<Int, ByteArray> = linkedMapOf(),
    ) {
        fun matches(chunk: BleFrameChunk): Boolean =
            senderPeerId == chunk.senderPeerId &&
                senderFingerprint == chunk.senderFingerprint &&
                chunkCount == chunk.chunkCount &&
                payloadSize == chunk.payloadSize &&
                payloadCrc32 == chunk.payloadCrc32
    }
}
