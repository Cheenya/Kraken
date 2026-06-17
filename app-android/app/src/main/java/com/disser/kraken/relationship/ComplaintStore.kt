package com.disser.kraken.relationship

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class ComplaintStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.COMPLAINTS, Context.MODE_PRIVATE)
    private val listSerializer = ListSerializer(ComplaintEvent.serializer())

    fun load(): List<ComplaintEvent> {
        val encoded = preferences.getString(KrakenStorageKeys.Complaints.LIST, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(listSerializer, encoded)
        }.getOrDefault(emptyList())
    }

    fun add(complaintEvent: ComplaintEvent): List<ComplaintEvent> {
        val updated = load() + complaintEvent
        save(updated)
        return updated
    }

    fun save(complaints: List<ComplaintEvent>) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Complaints.LIST, InvitePayloadCodec.json.encodeToString(listSerializer, complaints))
            .apply()
    }
}
