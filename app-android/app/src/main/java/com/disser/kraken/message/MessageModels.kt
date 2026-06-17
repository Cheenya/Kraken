package com.disser.kraken.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("relationship_id")
    val relationshipId: String,
    @SerialName("peer_fingerprint")
    val peerFingerprint: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Serializable
enum class MessageDirection {
    OUTGOING,
    INCOMING,
}

@Serializable
enum class MessageStatus {
    LOCAL_PENDING,
    READY_FOR_TRANSPORT,
    SENT_TO_TRANSPORT,
    DELIVERED_TO_PEER,
    FAILED,
}

@Serializable
data class LocalMessage(
    @SerialName("message_id")
    val messageId: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("relationship_id")
    val relationshipId: String,
    @SerialName("peer_fingerprint")
    val peerFingerprint: String,
    val direction: MessageDirection,
    val status: MessageStatus,
    @SerialName("body")
    val body: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @SerialName("reply_to_message_id")
    val replyToMessageId: String? = null,
    @SerialName("reply_to_body_preview")
    val replyToBodyPreview: String? = null,
    @SerialName("reply_to_sender_name")
    val replyToSenderName: String? = null,
)

@Serializable
data class SavedMessage(
    @SerialName("saved_message_id")
    val savedMessageId: String,
    @SerialName("source_message_id")
    val sourceMessageId: String,
    @SerialName("source_relationship_id")
    val sourceRelationshipId: String,
    @SerialName("source_display_name")
    val sourceDisplayName: String,
    @SerialName("body")
    val body: String,
    @SerialName("original_created_at_epoch_millis")
    val originalCreatedAtEpochMillis: Long,
    @SerialName("saved_at_epoch_millis")
    val savedAtEpochMillis: Long,
)
