package com.disser.kraken.message

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class MessageStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.MESSAGES, Context.MODE_PRIVATE)
    private val listSerializer = ListSerializer(LocalMessage.serializer())

    fun load(): List<LocalMessage> {
        val encoded = preferences.getString(KrakenStorageKeys.Messages.LIST, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(listSerializer, encoded)
        }.getOrDefault(emptyList())
    }

    fun add(message: LocalMessage): List<LocalMessage> {
        val updated = MessageService.pruneMessages(load() + message)
        save(updated)
        return updated
    }

    fun upsert(message: LocalMessage): List<LocalMessage> {
        val current = load()
        val updated = if (current.any { it.messageId == message.messageId }) {
            current.map { if (it.messageId == message.messageId) message else it }
        } else {
            current + message
        }
        val pruned = MessageService.pruneMessages(updated)
        save(pruned)
        return pruned
    }

    fun delete(messageId: String): List<LocalMessage> {
        val updated = load().filterNot { it.messageId == messageId }
        save(updated)
        return updated
    }

    fun clearConversation(conversationId: String): List<LocalMessage> {
        val updated = load().filterNot { it.conversationId == conversationId }
        save(updated)
        return updated
    }

    fun save(messages: List<LocalMessage>) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Messages.LIST, InvitePayloadCodec.json.encodeToString(listSerializer, MessageService.pruneMessages(messages)))
            .apply()
    }
}
