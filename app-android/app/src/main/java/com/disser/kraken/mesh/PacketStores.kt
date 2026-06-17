package com.disser.kraken.mesh

import android.content.Context
import com.disser.kraken.storage.KrakenStorageKeys

object PacketStoragePolicy {
    const val MAX_OUTBOX_PACKETS = 500
    const val MAX_INBOX_PACKETS = 500
    const val MAX_SEEN_PACKET_IDS = 2_000
    const val MAX_RECEIPTS = 500

    fun pruneStoredPackets(
        packets: List<StoredPacket>,
        limit: Int,
    ): List<StoredPacket> =
        packets
            .distinctBy { it.packet.packetId }
            .sortedBy { it.storedAtEpochMillis }
            .takeLast(limit)

    fun updateStoredPacketStatus(
        packets: List<StoredPacket>,
        packetId: String,
        status: PacketStoreStatus,
        lastError: MeshRejectionReason? = null,
    ): List<StoredPacket> =
        packets.map { stored ->
            if (stored.packet.packetId == packetId) {
                stored.copy(status = status, lastError = lastError)
            } else {
                stored
            }
        }

    fun updateStoredPacketAttempt(
        packets: List<StoredPacket>,
        packetId: String,
        status: PacketStoreStatus,
        nowEpochMillis: Long,
        nextAttemptAtEpochMillis: Long,
        lastError: MeshRejectionReason? = null,
    ): List<StoredPacket> =
        packets.map { stored ->
            if (stored.packet.packetId == packetId) {
                stored.copy(
                    status = status,
                    lastError = lastError,
                    attempts = stored.attempts + 1,
                    lastAttemptAtEpochMillis = nowEpochMillis,
                    nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
                )
            } else {
                stored
            }
        }

    fun queuedReceiptPackets(packets: List<StoredPacket>): List<StoredPacket> =
        packets.filter {
            it.packet.packetType == KrakenPacketType.RECEIPT &&
                it.status == PacketStoreStatus.QUEUED
        }

    fun queuedHandshakeConfirmationPackets(packets: List<StoredPacket>): List<StoredPacket> =
        packets.filter {
            it.packet.packetType == KrakenPacketType.HANDSHAKE_CONFIRMATION &&
                it.status == PacketStoreStatus.QUEUED
        }

    fun queuedHandshakeResponsePackets(packets: List<StoredPacket>): List<StoredPacket> =
        packets.filter {
            it.packet.packetType == KrakenPacketType.HANDSHAKE_RESPONSE &&
                it.status == PacketStoreStatus.QUEUED
        }

    fun eligibleHandshakeConfirmationPackets(
        packets: List<StoredPacket>,
        nowEpochMillis: Long,
        retryPolicy: TransportRetryPolicy,
    ): List<StoredPacket> =
        eligibleHandshakePackets(
            packets = packets,
            packetType = KrakenPacketType.HANDSHAKE_CONFIRMATION,
            nowEpochMillis = nowEpochMillis,
            retryPolicy = retryPolicy,
        )

    fun eligibleHandshakeResponsePackets(
        packets: List<StoredPacket>,
        nowEpochMillis: Long,
        retryPolicy: TransportRetryPolicy,
    ): List<StoredPacket> =
        eligibleHandshakePackets(
            packets = packets,
            packetType = KrakenPacketType.HANDSHAKE_RESPONSE,
            nowEpochMillis = nowEpochMillis,
            retryPolicy = retryPolicy,
        )

    private fun eligibleHandshakePackets(
        packets: List<StoredPacket>,
        packetType: KrakenPacketType,
        nowEpochMillis: Long,
        retryPolicy: TransportRetryPolicy,
    ): List<StoredPacket> =
        packets.filter { stored ->
            stored.packet.packetType == packetType &&
                stored.status in setOf(PacketStoreStatus.QUEUED, PacketStoreStatus.SENT) &&
                (
                    (
                        stored.status == PacketStoreStatus.QUEUED &&
                            stored.attempts == 0 &&
                            stored.nextAttemptAtEpochMillis <= nowEpochMillis &&
                            stored.packet.expiresAtEpochMillis > nowEpochMillis
                        ) ||
                        MeshTransportHardening.canAttemptSend(
                            QueuedPacketAttempt(
                                packetId = stored.packet.packetId,
                                attempts = stored.attempts,
                                nextAttemptAtEpochMillis = stored.nextAttemptAtEpochMillis,
                                expiresAtEpochMillis = stored.packet.expiresAtEpochMillis,
                            ),
                            nowEpochMillis,
                            retryPolicy,
                        )
                    )
        }

    fun eligibleOutgoingMessagePackets(
        packets: List<StoredPacket>,
        nowEpochMillis: Long,
        retryPolicy: TransportRetryPolicy,
    ): List<StoredPacket> =
        packets.filter { stored ->
            stored.packet.packetType == KrakenPacketType.MESSAGE &&
                stored.status in setOf(PacketStoreStatus.QUEUED, PacketStoreStatus.SENT) &&
                MeshTransportHardening.canAttemptSend(
                    QueuedPacketAttempt(
                        packetId = stored.packet.packetId,
                        attempts = stored.attempts,
                        nextAttemptAtEpochMillis = stored.nextAttemptAtEpochMillis,
                        expiresAtEpochMillis = stored.packet.expiresAtEpochMillis,
                    ),
                    nowEpochMillis,
                    retryPolicy,
                )
        }

    fun terminalOutgoingMessagePackets(
        packets: List<StoredPacket>,
        nowEpochMillis: Long,
        retryPolicy: TransportRetryPolicy,
    ): List<StoredPacket> =
        packets.filter { stored ->
            stored.packet.packetType == KrakenPacketType.MESSAGE &&
                stored.status in setOf(PacketStoreStatus.QUEUED, PacketStoreStatus.SENT) &&
                (stored.packet.expiresAtEpochMillis <= nowEpochMillis || stored.attempts >= retryPolicy.maxAttempts)
        }

    fun hasNonFinalMessagePacket(packets: List<StoredPacket>, messageId: String): Boolean =
        packets.any { stored ->
            stored.packet.packetType == KrakenPacketType.MESSAGE &&
                stored.packet.messageId == messageId &&
                stored.status in setOf(PacketStoreStatus.QUEUED, PacketStoreStatus.SENT)
        }

    fun deleteMessagePackets(packets: List<StoredPacket>, messageId: String): List<StoredPacket> =
        packets.filterNot { stored ->
            stored.packet.packetType == KrakenPacketType.MESSAGE && stored.packet.messageId == messageId
        }

    fun deleteConversationPackets(packets: List<StoredPacket>, conversationId: String): List<StoredPacket> =
        packets.filterNot { stored ->
            stored.packet.packetType == KrakenPacketType.MESSAGE && stored.packet.conversationId == conversationId
        }

    fun pruneSeenPackets(ids: List<SeenPacketId>): List<SeenPacketId> =
        ids
            .distinctBy { it.packetId }
            .sortedBy { it.seenAtEpochMillis }
            .takeLast(MAX_SEEN_PACKET_IDS)

    fun pruneReceipts(receipts: List<PacketReceipt>): List<PacketReceipt> =
        receipts
            .distinctBy { it.receiptId }
            .sortedBy { it.createdAtEpochMillis }
            .takeLast(MAX_RECEIPTS)
}

interface PacketOutbox {
    fun load(): List<StoredPacket>
    fun upsert(packet: StoredPacket): List<StoredPacket>
    fun markStatus(
        packetId: String,
        status: PacketStoreStatus,
        lastError: MeshRejectionReason? = null,
    ): List<StoredPacket>
    fun recordAttempt(
        packetId: String,
        status: PacketStoreStatus,
        nowEpochMillis: Long,
        nextAttemptAtEpochMillis: Long,
        lastError: MeshRejectionReason? = null,
    ): List<StoredPacket>
    fun queuedReceipts(): List<StoredPacket>
    fun eligibleHandshakeResponses(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket>
    fun eligibleHandshakeConfirmations(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket>
    fun eligibleOutgoingMessages(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket>
    fun terminalOutgoingMessages(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket>
    fun hasNonFinalMessagePacket(messageId: String): Boolean
    fun requeueMessagePackets(messageId: String, nowEpochMillis: Long): List<StoredPacket>
    fun deleteMessagePackets(messageId: String): List<StoredPacket>
    fun deleteConversationPackets(conversationId: String): List<StoredPacket>
}

interface PacketInbox {
    fun add(packet: StoredPacket): List<StoredPacket>
}

interface PacketSeen {
    fun contains(packetId: String): Boolean
    fun markSeen(packetId: String, nowEpochMillis: Long = System.currentTimeMillis()): List<SeenPacketId>
}

interface PacketReceipts {
    fun add(receipt: PacketReceipt): List<PacketReceipt>
}

class PacketOutboxStore(context: Context) : PacketOutbox {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.PACKET_OUTBOX, Context.MODE_PRIVATE)

    override fun load(): List<StoredPacket> =
        PacketStoragePolicy.pruneStoredPackets(
            MeshPacketCodec.decodeStoredPackets(preferences.getString(KrakenStorageKeys.Packets.OUTBOX, null)),
            PacketStoragePolicy.MAX_OUTBOX_PACKETS,
        )

    override fun upsert(packet: StoredPacket): List<StoredPacket> {
        val current = load().filterNot { it.packet.packetId == packet.packet.packetId }
        val updated = PacketStoragePolicy.pruneStoredPackets(current + packet, PacketStoragePolicy.MAX_OUTBOX_PACKETS)
        save(updated)
        return updated
    }

    override fun markStatus(
        packetId: String,
        status: PacketStoreStatus,
        lastError: MeshRejectionReason?,
    ): List<StoredPacket> {
        val updated = PacketStoragePolicy.updateStoredPacketStatus(load(), packetId, status, lastError)
        save(updated)
        return updated
    }

    override fun recordAttempt(
        packetId: String,
        status: PacketStoreStatus,
        nowEpochMillis: Long,
        nextAttemptAtEpochMillis: Long,
        lastError: MeshRejectionReason?,
    ): List<StoredPacket> {
        val updated = PacketStoragePolicy.updateStoredPacketAttempt(
            packets = load(),
            packetId = packetId,
            status = status,
            nowEpochMillis = nowEpochMillis,
            nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
            lastError = lastError,
        )
        save(updated)
        return updated
    }

    override fun queuedReceipts(): List<StoredPacket> =
        PacketStoragePolicy.queuedReceiptPackets(load())

    override fun eligibleHandshakeResponses(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket> =
        PacketStoragePolicy.eligibleHandshakeResponsePackets(load(), nowEpochMillis, retryPolicy)

    override fun eligibleHandshakeConfirmations(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket> =
        PacketStoragePolicy.eligibleHandshakeConfirmationPackets(load(), nowEpochMillis, retryPolicy)

    override fun eligibleOutgoingMessages(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket> =
        PacketStoragePolicy.eligibleOutgoingMessagePackets(load(), nowEpochMillis, retryPolicy)

    override fun terminalOutgoingMessages(nowEpochMillis: Long, retryPolicy: TransportRetryPolicy): List<StoredPacket> =
        PacketStoragePolicy.terminalOutgoingMessagePackets(load(), nowEpochMillis, retryPolicy)

    override fun hasNonFinalMessagePacket(messageId: String): Boolean =
        PacketStoragePolicy.hasNonFinalMessagePacket(load(), messageId)

    override fun requeueMessagePackets(messageId: String, nowEpochMillis: Long): List<StoredPacket> {
        val updated = load().map { stored ->
            if (stored.packet.packetType == KrakenPacketType.MESSAGE && stored.packet.messageId == messageId) {
                stored.copy(
                    status = PacketStoreStatus.QUEUED,
                    lastError = null,
                    nextAttemptAtEpochMillis = 0,
                    lastAttemptAtEpochMillis = nowEpochMillis,
                )
            } else {
                stored
            }
        }
        save(updated)
        return updated
    }

    override fun deleteMessagePackets(messageId: String): List<StoredPacket> {
        val updated = PacketStoragePolicy.deleteMessagePackets(load(), messageId)
        save(updated)
        return updated
    }

    override fun deleteConversationPackets(conversationId: String): List<StoredPacket> {
        val updated = PacketStoragePolicy.deleteConversationPackets(load(), conversationId)
        save(updated)
        return updated
    }

    fun save(packets: List<StoredPacket>) {
        val pruned = PacketStoragePolicy.pruneStoredPackets(packets, PacketStoragePolicy.MAX_OUTBOX_PACKETS)
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Packets.OUTBOX, MeshPacketCodec.encodeStoredPackets(pruned))
            .apply()
    }
}

class PacketInboxStore(context: Context) : PacketInbox {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.PACKET_INBOX, Context.MODE_PRIVATE)

    fun load(): List<StoredPacket> =
        PacketStoragePolicy.pruneStoredPackets(
            MeshPacketCodec.decodeStoredPackets(preferences.getString(KrakenStorageKeys.Packets.INBOX, null)),
            PacketStoragePolicy.MAX_INBOX_PACKETS,
        )

    override fun add(packet: StoredPacket): List<StoredPacket> {
        val updated = PacketStoragePolicy.pruneStoredPackets(load() + packet, PacketStoragePolicy.MAX_INBOX_PACKETS)
        save(updated)
        return updated
    }

    fun save(packets: List<StoredPacket>) {
        val pruned = PacketStoragePolicy.pruneStoredPackets(packets, PacketStoragePolicy.MAX_INBOX_PACKETS)
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Packets.INBOX, MeshPacketCodec.encodeStoredPackets(pruned))
            .apply()
    }
}

class PacketSeenStore(context: Context) : PacketSeen {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.PACKET_SEEN, Context.MODE_PRIVATE)

    fun load(): List<SeenPacketId> =
        PacketStoragePolicy.pruneSeenPackets(
            MeshPacketCodec.decodeSeenPackets(preferences.getString(KrakenStorageKeys.Packets.SEEN, null)),
        )

    override fun contains(packetId: String): Boolean =
        load().any { it.packetId == packetId }

    override fun markSeen(packetId: String, nowEpochMillis: Long): List<SeenPacketId> {
        val updated = PacketStoragePolicy.pruneSeenPackets(load() + SeenPacketId(packetId, nowEpochMillis))
        save(updated)
        return updated
    }

    fun save(ids: List<SeenPacketId>) {
        val pruned = PacketStoragePolicy.pruneSeenPackets(ids)
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Packets.SEEN, MeshPacketCodec.encodeSeenPackets(pruned))
            .apply()
    }
}

class ReceiptStore(context: Context) : PacketReceipts {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.RECEIPTS, Context.MODE_PRIVATE)

    fun load(): List<PacketReceipt> =
        PacketStoragePolicy.pruneReceipts(
            MeshPacketCodec.decodeReceipts(preferences.getString(KrakenStorageKeys.Packets.RECEIPTS, null)),
        )

    override fun add(receipt: PacketReceipt): List<PacketReceipt> {
        val updated = PacketStoragePolicy.pruneReceipts(load() + receipt)
        save(updated)
        return updated
    }

    fun save(receipts: List<PacketReceipt>) {
        val pruned = PacketStoragePolicy.pruneReceipts(receipts)
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Packets.RECEIPTS, MeshPacketCodec.encodeReceipts(pruned))
            .apply()
    }
}
