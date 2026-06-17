package com.disser.kraken.relay

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.encodeToString

class RelayPolicyStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.RELAY_POLICY, Context.MODE_PRIVATE)

    fun load(): RelayPolicyState {
        val encoded = preferences.getString(KrakenStorageKeys.RelayPolicy.STATE, null) ?: return RelayPolicyState.default()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(RelayPolicyState.serializer(), encoded)
        }.getOrDefault(RelayPolicyState.default())
    }

    fun save(state: RelayPolicyState): RelayPolicyState {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.RelayPolicy.STATE, InvitePayloadCodec.json.encodeToString(state))
            .apply()
        return state
    }

    fun selectMode(mode: RelayMode): RelayPolicyState =
        save(RelayPolicyState.forMode(mode))

}
