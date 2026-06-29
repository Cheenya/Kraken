package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPacketCodecTest {
    @Test
    fun packetRoundTripPreservesEnvelope() {
        val packet = packet()

        val decoded = MeshPacketCodec.decode(MeshPacketCodec.encode(packet)).getOrThrow()

        assertEquals(packet, decoded)
        assertEquals(KrakenPacket.PROTOTYPE_PROOF_MODE, decoded.proofMode)
        assertEquals(packet.cryptoProfileId, decoded.cryptoProfileId)
        assertEquals(packet.admissionDecisionHash, decoded.admissionDecisionHash)
        assertEquals(packet.profilePolicyVersion, decoded.profilePolicyVersion)
    }

    @Test
    fun invalidPacketFailsSafely() {
        assertTrue(MeshPacketCodec.decode("not-json").isFailure)
    }

    @Test
    fun duplicatePacketRejected() {
        val result = PacketValidator.validateForStorage(packet(), alreadySeen = true)

        assertFalse(result.accepted)
        assertEquals(MeshRejectionReason.DUPLICATE, result.rejectionReason)
    }

    @Test
    fun expiredPacketRejected() {
        val result = PacketValidator.validateForStorage(
            packet(expiresAtEpochMillis = 1_700_000_000_000),
            nowEpochMillis = 1_700_000_000_001,
        )

        assertFalse(result.accepted)
        assertEquals(MeshRejectionReason.EXPIRED, result.rejectionReason)
    }

    @Test
    fun exhaustedTtlRejectedWhenForwarding() {
        val result = PacketValidator.validateForStorage(
            packet(ttlHops = 0),
            nowEpochMillis = 1_700_000_000_000,
            requireForwardableTtl = true,
        )

        assertFalse(result.accepted)
        assertEquals(MeshRejectionReason.TTL_EXHAUSTED, result.rejectionReason)
    }

    @Test
    fun packetWithoutCryptoAdmissionMetadataIsRejected() {
        val result = PacketValidator.validateForStorage(
            packet().copy(
                cryptoProfileId = null,
                admissionDecisionHash = null,
                profilePolicyVersion = null,
            ),
            nowEpochMillis = 1_700_000_000_000,
        )

        assertFalse(result.accepted)
        assertEquals(MeshRejectionReason.UNKNOWN_CRYPTO_PROFILE, result.rejectionReason)
    }

    @Test
    fun outboxPruningKeepsLatestFiveHundred() {
        val stored = (0 until 505).map { index ->
            StoredPacket(
                packet = packet(packetId = "packet-$index"),
                status = PacketStoreStatus.QUEUED,
                storedAtEpochMillis = index.toLong(),
            )
        }

        val pruned = PacketStoragePolicy.pruneStoredPackets(stored, PacketStoragePolicy.MAX_OUTBOX_PACKETS)

        assertEquals(PacketStoragePolicy.MAX_OUTBOX_PACKETS, pruned.size)
        assertEquals("packet-5", pruned.first().packet.packetId)
        assertEquals("packet-504", pruned.last().packet.packetId)
    }

    @Test
    fun storedPacketStatusUpdatePreservesPayloadAndOnlyChangesStatusFields() {
        val original = StoredPacket(
            packet = packet(packetId = "packet-1"),
            status = PacketStoreStatus.QUEUED,
            storedAtEpochMillis = 42,
        )

        val updated = PacketStoragePolicy.updateStoredPacketStatus(
            packets = listOf(original),
            packetId = "packet-1",
            status = PacketStoreStatus.ACKED,
            lastError = MeshRejectionReason.UNKNOWN_PEER,
        ).single()

        assertEquals(original.packet, updated.packet)
        assertEquals(original.storedAtEpochMillis, updated.storedAtEpochMillis)
        assertEquals(PacketStoreStatus.ACKED, updated.status)
        assertEquals(MeshRejectionReason.UNKNOWN_PEER, updated.lastError)
    }

    @Test
    fun storedPacketAttemptUpdateTracksRetryMetadata() {
        val original = StoredPacket(
            packet = packet(packetId = "packet-1"),
            status = PacketStoreStatus.QUEUED,
            storedAtEpochMillis = 42,
        )

        val updated = PacketStoragePolicy.updateStoredPacketAttempt(
            packets = listOf(original),
            packetId = "packet-1",
            status = PacketStoreStatus.SENT,
            nowEpochMillis = 100,
            nextAttemptAtEpochMillis = 15_100,
        ).single()

        assertEquals(original.packet, updated.packet)
        assertEquals(1, updated.attempts)
        assertEquals(100L, updated.lastAttemptAtEpochMillis)
        assertEquals(15_100L, updated.nextAttemptAtEpochMillis)
        assertEquals(PacketStoreStatus.SENT, updated.status)
    }

    @Test
    fun eligibleOutgoingMessagesRespectBackoffAndTerminalStates() {
        val policy = TransportRetryPolicy(baseDelayMillis = 1_000, maxAttempts = 3)
        val ready = StoredPacket(
            packet = packet(packetId = "ready"),
            status = PacketStoreStatus.QUEUED,
            storedAtEpochMillis = 1,
            attempts = 1,
            nextAttemptAtEpochMillis = 2_000,
        )
        val waiting = ready.copy(
            packet = ready.packet.copy(packetId = "waiting"),
            nextAttemptAtEpochMillis = 5_000,
        )
        val acked = ready.copy(
            packet = ready.packet.copy(packetId = "acked"),
            status = PacketStoreStatus.ACKED,
        )

        val eligible = PacketStoragePolicy.eligibleOutgoingMessagePackets(
            packets = listOf(ready, waiting, acked),
            nowEpochMillis = 2_500,
            retryPolicy = policy,
        )

        assertEquals(listOf("ready"), eligible.map { it.packet.packetId })
        assertTrue(PacketStoragePolicy.hasNonFinalMessagePacket(listOf(ready), "message-1"))
        assertFalse(PacketStoragePolicy.hasNonFinalMessagePacket(listOf(acked), "message-1"))
    }

    @Test
    fun queuedReceiptFilterReturnsOnlyQueuedReceiptPackets() {
        val message = StoredPacket(packet("message-packet"), PacketStoreStatus.QUEUED, 1)
        val queuedReceipt = StoredPacket(
            packet("receipt-packet").copy(packetType = KrakenPacketType.RECEIPT, payloadType = PacketPayloadType.RECEIPT_JSON),
            PacketStoreStatus.QUEUED,
            2,
        )
        val sentReceipt = queuedReceipt.copy(
            packet = queuedReceipt.packet.copy(packetId = "sent-receipt"),
            status = PacketStoreStatus.SENT,
        )

        val queued = PacketStoragePolicy.queuedReceiptPackets(listOf(message, queuedReceipt, sentReceipt))

        assertEquals(listOf("receipt-packet"), queued.map { it.packet.packetId })
    }

    @Test
    fun deleteMessagePacketsRemovesOnlyMatchingMessagePackets() {
        val target = StoredPacket(packet("target"), PacketStoreStatus.QUEUED, 1)
        val otherMessage = StoredPacket(
            packet("other").copy(messageId = "message-2"),
            PacketStoreStatus.QUEUED,
            2,
        )
        val receiptForTarget = StoredPacket(
            packet("receipt").copy(
                packetType = KrakenPacketType.RECEIPT,
                payloadType = PacketPayloadType.RECEIPT_JSON,
            ),
            PacketStoreStatus.QUEUED,
            3,
        )

        val updated = PacketStoragePolicy.deleteMessagePackets(
            packets = listOf(target, otherMessage, receiptForTarget),
            messageId = "message-1",
        )

        assertEquals(listOf("other", "receipt"), updated.map { it.packet.packetId })
    }

    @Test
    fun deleteConversationPacketsRemovesOnlyMatchingConversationMessages() {
        val target = StoredPacket(packet("target"), PacketStoreStatus.QUEUED, 1)
        val otherConversation = StoredPacket(
            packet("other").copy(conversationId = "conversation-2", messageId = "message-2"),
            PacketStoreStatus.QUEUED,
            2,
        )
        val receiptForTarget = StoredPacket(
            packet("receipt").copy(
                packetType = KrakenPacketType.RECEIPT,
                payloadType = PacketPayloadType.RECEIPT_JSON,
            ),
            PacketStoreStatus.QUEUED,
            3,
        )

        val updated = PacketStoragePolicy.deleteConversationPackets(
            packets = listOf(target, otherConversation, receiptForTarget),
            conversationId = "conversation-1",
        )

        assertEquals(listOf("other", "receipt"), updated.map { it.packet.packetId })
    }

    @Test
    fun seenPruningKeepsLatestTwoThousand() {
        val seen = (0 until 2_005).map { index -> SeenPacketId("packet-$index", index.toLong()) }

        val pruned = PacketStoragePolicy.pruneSeenPackets(seen)

        assertEquals(PacketStoragePolicy.MAX_SEEN_PACKET_IDS, pruned.size)
        assertEquals("packet-5", pruned.first().packetId)
        assertEquals("packet-2004", pruned.last().packetId)
    }

    @Test
    fun receiptPruningKeepsLatestFiveHundred() {
        val receipts = (0 until 505).map { index ->
            PacketReceipt(
                receiptId = "receipt-$index",
                packetId = "packet-$index",
                messageId = "message-$index",
                senderFingerprint = "ALICE",
                recipientFingerprint = "BOB",
                createdAtEpochMillis = index.toLong(),
            )
        }

        val pruned = PacketStoragePolicy.pruneReceipts(receipts)

        assertEquals(PacketStoragePolicy.MAX_RECEIPTS, pruned.size)
        assertEquals("receipt-5", pruned.first().receiptId)
        assertEquals("receipt-504", pruned.last().receiptId)
    }

    private fun packet(
        packetId: String = "packet-1",
        ttlHops: Int = 4,
        expiresAtEpochMillis: Long = 1_700_000_600_000,
    ): KrakenPacket =
        KrakenPacket(
            packetId = packetId,
            packetType = KrakenPacketType.MESSAGE,
            senderFingerprint = "ALICE-FP",
            recipientFingerprint = "BOB-FP",
            relationshipId = "relationship-1",
            conversationId = "conversation-1",
            messageId = "message-1",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = expiresAtEpochMillis,
            ttlHops = ttlHops,
            payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
            payloadJson = """{"body":"hello"}""",
        )
}
