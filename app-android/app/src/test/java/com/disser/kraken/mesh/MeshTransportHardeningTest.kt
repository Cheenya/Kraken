package com.disser.kraken.mesh

import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshTransportHardeningTest {
    @Test
    fun retryBackoffIsBoundedAndStopsAfterMaxAttempts() {
        val policy = TransportRetryPolicy(baseDelayMillis = 1_000, maxDelayMillis = 5_000, maxAttempts = 2)
        val initial = QueuedPacketAttempt("packet-1", attempts = 0, nextAttemptAtEpochMillis = 0, expiresAtEpochMillis = 10_000)

        val first = MeshTransportHardening.nextRetry(initial, policy, nowEpochMillis = 1_000)
        val second = MeshTransportHardening.nextRetry(first, policy, nowEpochMillis = 2_000)

        assertEquals(1, first.attempts)
        assertEquals(2_000, first.nextAttemptAtEpochMillis)
        assertEquals(2, second.attempts)
        assertFalse(MeshTransportHardening.canAttemptSend(second, nowEpochMillis = 7_000, policy = policy))
    }

    @Test
    fun expiredQueuedMessageBecomesFailed() {
        val expired = MeshTransportHardening.expireMessageIfNeeded(
            message = message(MessageStatus.READY_FOR_TRANSPORT),
            expiresAtEpochMillis = 1_000,
            nowEpochMillis = 1_001,
        )

        assertEquals(MessageStatus.FAILED, expired.status)
    }

    @Test
    fun unacknowledgedSentMessageReturnsToQueueAfterTimeout() {
        val fresh = MeshTransportHardening.requeueUnacknowledgedSentMessage(
            message = message(MessageStatus.SENT_TO_TRANSPORT),
            ackTimeoutMillis = 15_000,
            nowEpochMillis = 1_700_000_005_000,
        )
        val stale = MeshTransportHardening.requeueUnacknowledgedSentMessage(
            message = message(MessageStatus.SENT_TO_TRANSPORT),
            ackTimeoutMillis = 15_000,
            nowEpochMillis = 1_700_000_015_000,
        )

        assertEquals(MessageStatus.SENT_TO_TRANSPORT, fresh.status)
        assertEquals(MessageStatus.READY_FOR_TRANSPORT, stale.status)
    }

    @Test
    fun rateLimitPreventsUnboundedSendLoop() {
        val policy = TransportRetryPolicy(maxPacketsPerMinute = 2)

        assertTrue(MeshTransportHardening.isRateLimited(RateLimitWindow(0, sentCount = 2), policy, nowEpochMillis = 30_000))
        assertFalse(MeshTransportHardening.isRateLimited(RateLimitWindow(0, sentCount = 2), policy, nowEpochMillis = 61_000))
    }

    @Test
    fun malformedFrameSizeIsIsolated() {
        assertEquals(MeshRejectionReason.MALFORMED, MeshTransportHardening.isolateMalformedFrame(0))
        assertEquals(MeshRejectionReason.MALFORMED, MeshTransportHardening.isolateMalformedFrame(LanFrameCodec.MAX_FRAME_BYTES + 5))
        assertNull(MeshTransportHardening.isolateMalformedFrame(128))
    }

    private fun message(status: MessageStatus): LocalMessage =
        LocalMessage(
            messageId = "message-1",
            conversationId = "conversation-1",
            relationshipId = "relationship-1",
            peerFingerprint = "BOB-FP",
            direction = MessageDirection.OUTGOING,
            status = status,
            body = "hello",
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
        )
}
