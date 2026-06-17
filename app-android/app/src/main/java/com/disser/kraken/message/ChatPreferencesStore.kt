package com.disser.kraken.message

import android.content.Context
import com.disser.kraken.storage.KrakenStorageKeys

data class ChatBackgroundPreset(
    val key: String,
    val title: String,
    val description: String,
)

class ChatPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.CHAT_PREFERENCES, Context.MODE_PRIVATE)

    fun loadQuickReaction(): String =
        preferences.getString(KrakenStorageKeys.ChatPreferences.QUICK_REACTION, DEFAULT_QUICK_REACTION)
            ?.takeIf { it in QUICK_REACTION_OPTIONS }
            ?: DEFAULT_QUICK_REACTION

    fun saveQuickReaction(reaction: String): String {
        val normalized = reaction.takeIf { it in QUICK_REACTION_OPTIONS } ?: DEFAULT_QUICK_REACTION
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.ChatPreferences.QUICK_REACTION, normalized)
            .apply()
        return normalized
    }

    fun loadGlobalBackground(): String =
        normalizeBackground(preferences.getString(KrakenStorageKeys.ChatPreferences.GLOBAL_BACKGROUND, null))

    fun saveGlobalBackground(backgroundKey: String): String {
        val normalized = normalizeBackground(backgroundKey)
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.ChatPreferences.GLOBAL_BACKGROUND, normalized)
            .apply()
        return normalized
    }

    fun loadRelationshipBackground(relationshipId: String): String? =
        preferences
            .getString(relationshipBackgroundKey(relationshipId), null)
            ?.takeIf(::isKnownBackground)

    fun saveRelationshipBackground(relationshipId: String, backgroundKey: String?): String? {
        val editor = preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
        val normalized = backgroundKey?.takeIf(::isKnownBackground)
        if (normalized == null) {
            editor.remove(relationshipBackgroundKey(relationshipId))
        } else {
            editor.putString(relationshipBackgroundKey(relationshipId), normalized)
        }
        editor.apply()
        return normalized
    }

    private fun relationshipBackgroundKey(relationshipId: String): String =
        KrakenStorageKeys.ChatPreferences.RELATIONSHIP_BACKGROUND_PREFIX + relationshipId

    companion object {
        const val DEFAULT_QUICK_REACTION = "👍"
        const val DEFAULT_BACKGROUND = "kraken_grid"
        val QUICK_REACTION_OPTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
        val BACKGROUND_PRESETS = listOf(
            ChatBackgroundPreset(
                key = DEFAULT_BACKGROUND,
                title = "Kraken grid",
                description = "Фирменный тёмный фон с тихим mesh-рисунком.",
            ),
            ChatBackgroundPreset(
                key = "solid_dark",
                title = "Чистый тёмный",
                description = "Ровный фон без рисунка для спокойной переписки.",
            ),
            ChatBackgroundPreset(
                key = "amoled_black",
                title = "AMOLED",
                description = "Настоящий чёрный фон для OLED/AMOLED экранов.",
            ),
            ChatBackgroundPreset(
                key = "deep_signal",
                title = "Глубокий сигнал",
                description = "Тёмный фон с редкими точками связи.",
            ),
        )

        fun normalizeBackground(backgroundKey: String?): String =
            backgroundKey?.takeIf(::isKnownBackground) ?: DEFAULT_BACKGROUND

        fun backgroundTitle(backgroundKey: String?): String =
            BACKGROUND_PRESETS.first { it.key == normalizeBackground(backgroundKey) }.title

        fun isKnownBackground(backgroundKey: String): Boolean =
            BACKGROUND_PRESETS.any { it.key == backgroundKey }
    }
}
