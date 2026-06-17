package com.disser.kraken.ui.theme

import android.content.Context
import com.disser.kraken.storage.KrakenStorageKeys

class ThemePresetStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        KrakenStorageKeys.Preferences.APPEARANCE,
        Context.MODE_PRIVATE,
    )

    fun load(): KrakenThemePreset {
        val defaultApplied = preferences.getBoolean(KrakenStorageKeys.Appearance.QUIET_GRAPHITE_DEFAULT_APPLIED, false)
        if (!defaultApplied) {
            preferences.edit()
                .putBoolean(KrakenStorageKeys.Appearance.QUIET_GRAPHITE_DEFAULT_APPLIED, true)
                .putString(KrakenStorageKeys.Appearance.UI_STYLE, KrakenThemePreset.DEFAULT.storageKey)
                .apply()
            return KrakenThemePreset.DEFAULT
        }

        return KrakenThemePreset.fromStorageKey(preferences.getString(KrakenStorageKeys.Appearance.UI_STYLE, null))
    }

    fun save(preset: KrakenThemePreset): KrakenThemePreset {
        preferences.edit()
            .putString(KrakenStorageKeys.Appearance.UI_STYLE, preset.storageKey)
            .apply()
        return preset
    }
}
