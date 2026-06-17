package com.disser.kraken.mesh

import com.disser.kraken.invite.InvitePayloadCodec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

object MeshPacketCodec {
    private val packetListSerializer = ListSerializer(StoredPacket.serializer())
    private val seenListSerializer = ListSerializer(SeenPacketId.serializer())
    private val receiptListSerializer = ListSerializer(PacketReceipt.serializer())

    fun encode(packet: KrakenPacket): String =
        InvitePayloadCodec.json.encodeToString(packet)

    fun decode(encoded: String): Result<KrakenPacket> =
        runCatching { InvitePayloadCodec.json.decodeFromString<KrakenPacket>(encoded) }
            .recoverCatching { error ->
                if (error is SerializationException || error is IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid Kraken packet.")
                }
                throw error
            }

    fun encodeStoredPackets(packets: List<StoredPacket>): String =
        InvitePayloadCodec.json.encodeToString(packetListSerializer, packets)

    fun decodeStoredPackets(encoded: String?): List<StoredPacket> =
        if (encoded.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { InvitePayloadCodec.json.decodeFromString(packetListSerializer, encoded) }
                .getOrDefault(emptyList())
        }

    fun encodeSeenPackets(ids: List<SeenPacketId>): String =
        InvitePayloadCodec.json.encodeToString(seenListSerializer, ids)

    fun decodeSeenPackets(encoded: String?): List<SeenPacketId> =
        if (encoded.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { InvitePayloadCodec.json.decodeFromString(seenListSerializer, encoded) }
                .getOrDefault(emptyList())
        }

    fun encodeReceipts(receipts: List<PacketReceipt>): String =
        InvitePayloadCodec.json.encodeToString(receiptListSerializer, receipts)

    fun decodeReceipts(encoded: String?): List<PacketReceipt> =
        if (encoded.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { InvitePayloadCodec.json.decodeFromString(receiptListSerializer, encoded) }
                .getOrDefault(emptyList())
        }
}
