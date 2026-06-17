package com.disser.kraken.relationship

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class RelationshipStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.RELATIONSHIPS, Context.MODE_PRIVATE)
    private val listSerializer = ListSerializer(Relationship.serializer())

    fun load(): List<Relationship> {
        val encoded = preferences.getString(KrakenStorageKeys.Relationships.LIST, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(listSerializer, encoded)
        }.getOrDefault(emptyList())
    }

    fun add(relationship: Relationship): List<Relationship> {
        val updated = load() + relationship
        save(updated)
        return updated
    }

    fun update(relationship: Relationship): List<Relationship> {
        val updated = load().map { current ->
            if (current.relationshipId == relationship.relationshipId) relationship else current
        }
        save(updated)
        return updated
    }

    fun remove(relationshipId: String): List<Relationship> {
        val updated = load().filterNot { it.relationshipId == relationshipId }
        save(updated)
        return updated
    }

    fun save(relationships: List<Relationship>) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Relationships.LIST, InvitePayloadCodec.json.encodeToString(listSerializer, relationships))
            .apply()
    }
}
