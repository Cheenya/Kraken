package com.disser.kraken.message

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class SavedMessageStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.MESSAGES, Context.MODE_PRIVATE)
    private val listSerializer = ListSerializer(SavedMessage.serializer())

    fun load(): List<SavedMessage> {
        val encoded = preferences.getString(KrakenStorageKeys.Messages.SAVED_LIST, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(listSerializer, encoded)
        }.getOrDefault(emptyList())
    }

    fun add(messages: List<SavedMessage>): List<SavedMessage> {
        if (messages.isEmpty()) return load()
        val current = load()
        val bySource = (current + messages).distinctBy { it.sourceMessageId }
        save(bySource.sortedByDescending { it.savedAtEpochMillis })
        return load()
    }

    private fun save(messages: List<SavedMessage>) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Messages.SAVED_LIST, InvitePayloadCodec.json.encodeToString(listSerializer, messages))
            .apply()
    }
}
