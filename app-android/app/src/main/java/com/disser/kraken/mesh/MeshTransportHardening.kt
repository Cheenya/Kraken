package com.disser.kraken.mesh

import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageService
import com.disser.kraken.message.MessageStatus

data class TransportRetryPolicy(
    val baseDelayMillis: Long = 1_000,
    val maxDelayMillis: Long = 60_000,
    val maxAttempts: Int = 5,
    val maxPacketsPerMinute: Int = 60,
)

data class QueuedPacketAttempt(
    val packetId: String,
    val attempts: Int,
    val nextAttemptAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

data class RateLimitWindow(
    val startedAtEpochMillis: Long,
    val sentCount: Int,
)

object MeshTransportHardening {
    fun nextRetry(
        current: QueuedPacketAttempt,
        policy: TransportRetryPolicy,
        nowEpochMillis: Long,
    ): QueuedPacketAttempt {
        val attempts = current.attempts + 1
        val delay = (policy.baseDelayMillis * (1L shl (attempts - 1).coerceAtMost(10)))
            .coerceAtMost(policy.maxDelayMillis)
        return current.copy(
            attempts = attempts,
            nextAttemptAtEpochMillis = nowEpochMillis + delay,
        )
    }

    fun canAttemptSend(attempt: QueuedPacketAttempt, nowEpochMillis: Long, policy: TransportRetryPolicy): Boolean =
        attempt.attempts < policy.maxAttempts &&
            attempt.expiresAtEpochMillis > nowEpochMillis &&
            attempt.nextAttemptAtEpochMillis <= nowEpochMillis

    fun expireMessageIfNeeded(
        message: LocalMessage,
        expiresAtEpochMillis: Long,
        nowEpochMillis: Long,
    ): LocalMessage =
        if (expiresAtEpochMillis <= nowEpochMillis) {
            MessageService.updateStatus(message, MessageStatus.FAILED, nowEpochMillis)
        } else {
            message
        }

    fun requeueUnacknowledgedSentMessage(
        message: LocalMessage,
        ackTimeoutMillis: Long,
        nowEpochMillis: Long,
    ): LocalMessage =
        if (
            message.direction == MessageDirection.OUTGOING &&
            message.status == MessageStatus.SENT_TO_TRANSPORT &&
            nowEpochMillis - message.updatedAtEpochMillis >= ackTimeoutMillis
        ) {
            MessageService.updateStatus(message, MessageStatus.READY_FOR_TRANSPORT, nowEpochMillis)
        } else {
            message
        }

    fun isRateLimited(
        window: RateLimitWindow,
        policy: TransportRetryPolicy,
        nowEpochMillis: Long,
    ): Boolean =
        nowEpochMillis - window.startedAtEpochMillis < 60_000 &&
            window.sentCount >= policy.maxPacketsPerMinute

    fun isolateMalformedFrame(rawFrameSize: Int): MeshRejectionReason? =
        if (rawFrameSize <= 0 || rawFrameSize > LanFrameCodec.MAX_FRAME_BYTES + 4) {
            MeshRejectionReason.MALFORMED
        } else {
            null
        }
}
