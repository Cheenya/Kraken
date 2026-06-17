package com.disser.kraken.invite

import android.content.Context
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class PendingInviteStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.PENDING_INVITES, Context.MODE_PRIVATE)
    private val listSerializer = ListSerializer(PendingInviteImport.serializer())

    fun load(): List<PendingInviteImport> {
        val encoded = preferences.getString(KrakenStorageKeys.PendingInvites.IMPORTS, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(listSerializer, encoded)
        }.getOrDefault(emptyList())
    }

    fun add(pendingImport: PendingInviteImport): List<PendingInviteImport> {
        val updated = load() + pendingImport
        save(updated)
        return updated
    }

    fun removeByInviteId(inviteId: String): List<PendingInviteImport> {
        val updated = load().filterNot { it.inviteId == inviteId }
        save(updated)
        return updated
    }

    fun save(pendingImports: List<PendingInviteImport>) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.PendingInvites.IMPORTS, InvitePayloadCodec.json.encodeToString(listSerializer, pendingImports))
            .apply()
    }
}
