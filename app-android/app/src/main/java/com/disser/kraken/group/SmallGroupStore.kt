package com.disser.kraken.group

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class SmallGroupStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.SMALL_GROUPS, Context.MODE_PRIVATE)
    private val groupSerializer = ListSerializer(SmallGroup.serializer())
    private val memberSerializer = ListSerializer(SmallGroupMember.serializer())
    private val messageSerializer = ListSerializer(SmallGroupMessagePlaceholder.serializer())

    fun snapshot(): SmallGroupSnapshot =
        SmallGroupSnapshot(
            groups = loadGroups(),
            members = loadMembers(),
            messages = loadMessages(),
        )

    fun addDemoGroup(creation: DemoSmallGroupCreation): SmallGroupSnapshot {
        saveList(KrakenStorageKeys.SmallGroups.LIST, groupSerializer, loadGroups() + creation.group)
        saveList(KrakenStorageKeys.SmallGroups.MEMBERS, memberSerializer, loadMembers() + creation.ownerMembership)
        return snapshot()
    }

    fun saveSnapshot(snapshot: SmallGroupSnapshot): SmallGroupSnapshot {
        saveList(KrakenStorageKeys.SmallGroups.LIST, groupSerializer, snapshot.groups)
        saveList(KrakenStorageKeys.SmallGroups.MEMBERS, memberSerializer, snapshot.members)
        saveList(KrakenStorageKeys.SmallGroups.MESSAGES, messageSerializer, snapshot.messages)
        return snapshot()
    }

    private fun loadGroups(): List<SmallGroup> =
        loadList(KrakenStorageKeys.SmallGroups.LIST, groupSerializer)

    private fun loadMembers(): List<SmallGroupMember> =
        loadList(KrakenStorageKeys.SmallGroups.MEMBERS, memberSerializer)

    private fun loadMessages(): List<SmallGroupMessagePlaceholder> =
        loadList(KrakenStorageKeys.SmallGroups.MESSAGES, messageSerializer)

    private fun <T> loadList(key: String, serializer: KSerializer<List<T>>): List<T> {
        val encoded = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(serializer, encoded)
        }.getOrDefault(emptyList())
    }

    private fun <T> saveList(
        key: String,
        serializer: KSerializer<List<T>>,
        values: List<T>,
    ) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(key, InvitePayloadCodec.json.encodeToString(serializer, values))
            .apply()
    }
}
