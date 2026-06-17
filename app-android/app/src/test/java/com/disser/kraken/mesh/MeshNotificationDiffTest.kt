package com.disser.kraken.mesh

import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshNotificationDiffTest {
    @Test
    fun reportsOnlyNewIncomingMessages() {
        val existingIncoming = message("m1", MessageDirection.INCOMING, 1_000)
        val existingOutgoing = message("m2", MessageDirection.OUTGOING, 2_000)
        val newOutgoing = message("m3", MessageDirection.OUTGOING, 3_000)
        val newIncoming = message("m4", MessageDirection.INCOMING, 4_000)

        val result = MeshNotificationDiff.newIncomingMessages(
            before = listOf(existingIncoming, existingOutgoing),
            after = listOf(existingIncoming, existingOutgoing, newOutgoing, newIncoming),
        )

        assertEquals(listOf(newIncoming), result)
    }

    @Test
    fun duplicateIncomingMessagesDoNotNotifyAgain() {
        val incoming = message("m1", MessageDirection.INCOMING, 1_000)

        val result = MeshNotificationDiff.newIncomingMessages(
            before = listOf(incoming),
            after = listOf(incoming),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun notifierBatchesMessagesByRelationship() {
        val firstContactMessage = message(
            id = "m1",
            direction = MessageDirection.INCOMING,
            createdAt = 1_000,
            relationshipId = "relationship-1",
            body = "first",
        )
        val secondContactMessage = message(
            id = "m2",
            direction = MessageDirection.INCOMING,
            createdAt = 2_000,
            relationshipId = "relationship-2",
            body = "other",
        )
        val firstContactLatest = message(
            id = "m3",
            direction = MessageDirection.INCOMING,
            createdAt = 3_000,
            relationshipId = "relationship-1",
            body = "latest",
        )

        val result = KrakenMessageNotifier.notificationBatches(
            listOf(firstContactMessage, secondContactMessage, firstContactLatest),
        )

        assertEquals(listOf("relationship-2", "relationship-1"), result.map { it.relationshipId })
        assertEquals(listOf("first", "latest"), result.last().messages.map { it.body })
    }

    @Test
    fun notifierKeepsRecentMessagesInsideOneRelationshipBatch() {
        val messages = (1..6).map { index ->
            message(
                id = "m$index",
                direction = MessageDirection.INCOMING,
                createdAt = index * 1_000L,
                relationshipId = "relationship-1",
                body = "message $index",
            )
        }

        val result = KrakenMessageNotifier.notificationBatches(messages)

        assertEquals(1, result.size)
        assertEquals("relationship-1", result.single().relationshipId)
        assertEquals(messages.map { it.body }, result.single().messages.map { it.body })
    }

    @Test
    fun syncMergePreservesMessagesAddedWhileSyncWasRunning() {
        val existing = message("m1", MessageDirection.OUTGOING, 1_000)
        val syncedExisting = existing.copy(status = MessageStatus.SENT_TO_TRANSPORT, updatedAtEpochMillis = 2_000)
        val quicklyAdded = message("m2", MessageDirection.OUTGOING, 3_000)

        val result = MeshRuntime.mergeConcurrentMessages(
            before = listOf(existing),
            synced = listOf(syncedExisting),
            current = listOf(existing, quicklyAdded),
        )

        assertEquals(listOf("m1", "m2"), result.map { it.messageId })
        assertEquals(MessageStatus.SENT_TO_TRANSPORT, result.first { it.messageId == "m1" }.status)
        assertEquals("hello", result.first { it.messageId == "m2" }.body)
    }

    private fun message(
        id: String,
        direction: MessageDirection,
        createdAt: Long,
        relationshipId: String = "relationship-1",
        body: String = "hello",
    ): LocalMessage =
        LocalMessage(
            messageId = id,
            conversationId = "conversation-1",
            relationshipId = relationshipId,
            peerFingerprint = "PEER",
            direction = direction,
            status = MessageStatus.DELIVERED_TO_PEER,
            body = body,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = createdAt,
        )
}
