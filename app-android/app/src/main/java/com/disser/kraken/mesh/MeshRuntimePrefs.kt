package com.disser.kraken.mesh

import android.content.Context
import com.disser.kraken.storage.KrakenStorageKeys

class MeshRuntimePrefs(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        KrakenStorageKeys.Preferences.MESH_RUNTIME,
        Context.MODE_PRIVATE,
    )

    var meshEnabled: Boolean
        get() = preferences.getBoolean(KrakenStorageKeys.MeshRuntime.MESH_ENABLED, false)
        set(value) {
            preferences.edit()
                .putBoolean(KrakenStorageKeys.MeshRuntime.MESH_ENABLED, value)
                .apply()
        }

    var wifiDirectEnabled: Boolean
        get() = preferences.getBoolean(KrakenStorageKeys.MeshRuntime.WIFI_DIRECT_ENABLED, true)
        set(value) {
            preferences.edit()
                .putBoolean(KrakenStorageKeys.MeshRuntime.WIFI_DIRECT_ENABLED, value)
                .apply()
        }

    var transportProfile: String
        get() = preferences.getString(
            KrakenStorageKeys.MeshRuntime.TRANSPORT_PROFILE,
            MeshTransportSelection.PROFILE_HOTSPOT_COMPATIBLE,
        ) ?: MeshTransportSelection.PROFILE_HOTSPOT_COMPATIBLE
        set(value) {
            preferences.edit()
                .putString(KrakenStorageKeys.MeshRuntime.TRANSPORT_PROFILE, value)
                .putBoolean(
                    KrakenStorageKeys.MeshRuntime.WIFI_DIRECT_ENABLED,
                    MeshTransportSelection.fromProfileId(value).wifiDirect,
                )
                .apply()
        }

    val transportSelection: MeshTransportSelection
        get() = MeshTransportSelection.fromProfileId(transportProfile)

    var lastServiceStartedAtEpochMillis: Long
        get() = preferences.getLong(KrakenStorageKeys.MeshRuntime.LAST_SERVICE_STARTED_AT, 0L)
        set(value) {
            preferences.edit()
                .putLong(KrakenStorageKeys.MeshRuntime.LAST_SERVICE_STARTED_AT, value)
                .apply()
        }
}
