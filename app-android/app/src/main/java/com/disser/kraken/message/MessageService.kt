package com.disser.kraken.message

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService
import java.util.UUID

object MessageService {
    const val MAX_MESSAGES_PER_CONVERSATION = 200

    val conversationOrderComparator: Comparator<LocalMessage> =
        compareBy<LocalMessage> { it.createdAtEpochMillis }
            .thenBy { it.messageId }

    fun conversationIdFor(relationship: Relationship): String =
        "conversation-${relationship.relationshipId}"

    fun conversationFor(
        relationship: Relationship,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Conversation =
        Conversation(
            conversationId = conversationIdFor(relationship),
            relationshipId = relationship.relationshipId,
            peerFingerprint = relationship.peerFingerprint,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )

    fun createOutgoingMessage(
        localIdentity: LocalIdentity,
        relationship: Relationship,
        body: String,
        replyToMessage: LocalMessage? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): LocalMessage {
        require(RelationshipService.canSendMessage(relationship)) {
            "Messages can only be created for ACTIVE relationships."
        }
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "Message body must not be blank." }
        require(relationship.localIdentityPublicKey == localIdentity.publicKeyEncoded) {
            "Relationship does not belong to the local identity."
        }
        return LocalMessage(
            messageId = "message-${UUID.randomUUID()}",
            conversationId = conversationIdFor(relationship),
            relationshipId = relationship.relationshipId,
            peerFingerprint = relationship.peerFingerprint,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.READY_FOR_TRANSPORT,
            body = trimmed,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            replyToMessageId = replyToMessage?.messageId,
            replyToBodyPreview = replyToMessage?.body?.replyPreview(),
            replyToSenderName = replyToMessage?.let { reply ->
                if (reply.direction == MessageDirection.OUTGOING) {
                    "Вы"
                } else {
                    relationship.peerDisplayName?.takeIf { it.isNotBlank() } ?: "Контакт"
                }
            },
        )
    }

    fun createIncomingMessage(
        relationship: Relationship,
        messageId: String,
        body: String,
        replyToMessageId: String? = null,
        replyToBodyPreview: String? = null,
        replyToSenderName: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): LocalMessage {
        require(RelationshipService.canSendMessage(relationship)) {
            "Incoming messages are accepted only for ACTIVE relationships."
        }
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "Message body must not be blank." }
        return LocalMessage(
            messageId = messageId,
            conversationId = conversationIdFor(relationship),
            relationshipId = relationship.relationshipId,
            peerFingerprint = relationship.peerFingerprint,
            direction = MessageDirection.INCOMING,
            status = MessageStatus.DELIVERED_TO_PEER,
            body = trimmed,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            replyToMessageId = replyToMessageId?.takeIf { it.isNotBlank() },
            replyToBodyPreview = replyToBodyPreview?.replyPreview(),
            replyToSenderName = replyToSenderName?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    fun updateStatus(
        message: LocalMessage,
        status: MessageStatus,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): LocalMessage {
        val nextStatus = if (
            message.status == MessageStatus.DELIVERED_TO_PEER &&
            status != MessageStatus.DELIVERED_TO_PEER
        ) {
            MessageStatus.DELIVERED_TO_PEER
        } else {
            status
        }
        return if (message.status == nextStatus) {
            message
        } else {
            message.copy(status = nextStatus, updatedAtEpochMillis = nowEpochMillis)
        }
    }

    fun sortConversationMessages(messages: List<LocalMessage>): List<LocalMessage> =
        messages.sortedWith(conversationOrderComparator)

    fun pruneMessages(messages: List<LocalMessage>): List<LocalMessage> =
        messages
            .groupBy { it.conversationId }
            .values
            .flatMap { conversationMessages ->
                sortConversationMessages(conversationMessages)
                    .takeLast(MAX_MESSAGES_PER_CONVERSATION)
            }
            .sortedWith(conversationOrderComparator)

    private fun String.replyPreview(): String? =
        replace('\n', ' ')
            .trim()
            .take(REPLY_PREVIEW_LIMIT)
            .takeIf { it.isNotBlank() }

    private const val REPLY_PREVIEW_LIMIT = 96
}
