package com.disser.kraken.mesh

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageService
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class KrakenNotificationInboxStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.NOTIFICATION_INBOX, Context.MODE_PRIVATE)
    private val listSerializer = ListSerializer(LocalMessage.serializer())

    fun load(): List<LocalMessage> {
        val encoded = preferences.getString(KrakenStorageKeys.NotificationInbox.MESSAGES, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(listSerializer, encoded)
        }.getOrDefault(emptyList())
    }

    fun addIncoming(messages: List<LocalMessage>): List<LocalMessage> {
        if (messages.isEmpty()) return load()
        val currentById = load().associateBy { it.messageId }
        val merged = currentById.toMutableMap()
        messages
            .filter { it.direction == MessageDirection.INCOMING }
            .forEach { merged[it.messageId] = it }
        val pruned = merged.values
            .groupBy { it.relationshipId }
            .values
            .flatMap { relationshipMessages ->
                MessageService
                    .sortConversationMessages(relationshipMessages)
                    .takeLast(MAX_MESSAGES_PER_RELATIONSHIP)
            }
            .let(MessageService::sortConversationMessages)
        save(pruned)
        return pruned
    }

    fun messagesFor(relationshipId: String): List<LocalMessage> =
        load()
            .filter { it.relationshipId == relationshipId }
            .let(MessageService::sortConversationMessages)

    fun clearRelationship(relationshipId: String): List<LocalMessage> {
        val updated = load().filterNot { it.relationshipId == relationshipId }
        save(updated)
        return updated
    }

    private fun save(messages: List<LocalMessage>) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.NotificationInbox.MESSAGES, InvitePayloadCodec.json.encodeToString(listSerializer, messages))
            .apply()
    }

    companion object {
        private const val MAX_MESSAGES_PER_RELATIONSHIP = 8
    }
}
