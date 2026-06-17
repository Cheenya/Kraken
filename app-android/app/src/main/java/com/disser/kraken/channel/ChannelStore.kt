package com.disser.kraken.channel

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class ChannelStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.CHANNELS, Context.MODE_PRIVATE)
    private val channelSerializer = ListSerializer(Channel.serializer())
    private val membershipSerializer = ListSerializer(ChannelMembership.serializer())
    private val messageSerializer = ListSerializer(ChannelMessage.serializer())

    fun snapshot(): ChannelSnapshot =
        ChannelSnapshot(
            channels = loadChannels(),
            memberships = loadMemberships(),
            messages = loadMessages(),
        )

    fun addDemoChannel(creation: DemoChannelCreation): ChannelSnapshot {
        saveList(KrakenStorageKeys.Channels.LIST, channelSerializer, loadChannels() + creation.channel)
        saveList(KrakenStorageKeys.Channels.MEMBERSHIPS, membershipSerializer, loadMemberships() + creation.membership)
        return snapshot()
    }

    fun updateMembership(membership: ChannelMembership): ChannelSnapshot {
        val updated = loadMemberships().map { current ->
            if (current.channelId == membership.channelId && current.memberPublicKey == membership.memberPublicKey) {
                membership
            } else {
                current
            }
        }
        saveList(KrakenStorageKeys.Channels.MEMBERSHIPS, membershipSerializer, updated)
        return snapshot()
    }

    fun saveSnapshot(snapshot: ChannelSnapshot): ChannelSnapshot {
        saveList(KrakenStorageKeys.Channels.LIST, channelSerializer, snapshot.channels)
        saveList(KrakenStorageKeys.Channels.MEMBERSHIPS, membershipSerializer, snapshot.memberships)
        saveList(KrakenStorageKeys.Channels.MESSAGES, messageSerializer, snapshot.messages)
        return snapshot()
    }

    private fun loadChannels(): List<Channel> =
        loadList(KrakenStorageKeys.Channels.LIST, channelSerializer)

    private fun loadMemberships(): List<ChannelMembership> =
        loadList(KrakenStorageKeys.Channels.MEMBERSHIPS, membershipSerializer)

    private fun loadMessages(): List<ChannelMessage> =
        loadList(KrakenStorageKeys.Channels.MESSAGES, messageSerializer)

    private fun <T> loadList(key: String, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> {
        val encoded = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(serializer, encoded)
        }.getOrDefault(emptyList())
    }

    private fun <T> saveList(
        key: String,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
        values: List<T>,
    ) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(key, InvitePayloadCodec.json.encodeToString(serializer, values))
            .apply()
    }
}
