package com.disser.kraken.ui.theme

data class KrakenThemePresetOption(
    val preset: KrakenThemePreset,
    val selected: Boolean,
)

enum class KrakenDensityMode {
    COMFORTABLE,
    COMPACT,
    TECHNICAL,
}

enum class KrakenSurfaceStyle {
    SOLID,
    GLASS_LIKE,
    CONSOLE,
}

enum class KrakenThemePreset(
    val storageKey: String,
    val displayName: String,
    val description: String,
    val densityMode: KrakenDensityMode,
    val surfaceStyle: KrakenSurfaceStyle,
) {
    KRAKEN_DARK(
        storageKey = "kraken_dark",
        displayName = "Тихий графит",
        description = "Графитовый интерфейс для переписок со спокойным акцентом приватности.",
        densityMode = KrakenDensityMode.COMPACT,
        surfaceStyle = KrakenSurfaceStyle.SOLID,
    ),
    LIQUID_GLASS(
        storageKey = "liquid_glass",
        displayName = "Стеклянная консоль",
        description = "Мягкие многослойные поверхности для демонстрации и спокойной навигации.",
        densityMode = KrakenDensityMode.COMFORTABLE,
        surfaceStyle = KrakenSurfaceStyle.GLASS_LIKE,
    ),
    ABYSS(
        storageKey = "abyss",
        displayName = "Глубина",
        description = "Глубокая тёмная тема с акцентами фирменного Kraken-стиля.",
        densityMode = KrakenDensityMode.COMFORTABLE,
        surfaceStyle = KrakenSurfaceStyle.GLASS_LIKE,
    ),
    SIGNAL_CLEAN(
        storageKey = "signal_clean",
        displayName = "Чистая приватность",
        description = "Минималистичный интерфейс с приоритетом приватности и минимумом шума.",
        densityMode = KrakenDensityMode.COMFORTABLE,
        surfaceStyle = KrakenSurfaceStyle.SOLID,
    ),
    COMPACT_MESSENGER(
        storageKey = "compact_messenger",
        displayName = "Компактный мессенджер",
        description = "Компактная раскладка мессенджера для плотных списков и частого тестирования.",
        densityMode = KrakenDensityMode.COMPACT,
        surfaceStyle = KrakenSurfaceStyle.SOLID,
    ),
    AMOLED_BLACK(
        storageKey = "amoled_black",
        displayName = "AMOLED чёрная",
        description = "Настоящий чёрный фон для OLED/AMOLED экранов с аккуратными cyan-акцентами.",
        densityMode = KrakenDensityMode.COMPACT,
        surfaceStyle = KrakenSurfaceStyle.SOLID,
    ),
    RESEARCH_CONSOLE(
        storageKey = "research_console",
        displayName = "Исследовательская консоль",
        description = "Технический стиль для исследовательских и демонстрационных экранов.",
        densityMode = KrakenDensityMode.TECHNICAL,
        surfaceStyle = KrakenSurfaceStyle.CONSOLE,
    );

    companion object {
        val DEFAULT: KrakenThemePreset = KRAKEN_DARK

        fun fromStorageKey(value: String?): KrakenThemePreset =
            entries.firstOrNull { it.storageKey == value }
                ?: legacyStorageKeyAliases[value]
                ?: DEFAULT

        private val legacyStorageKeyAliases: Map<String?, KrakenThemePreset> =
            mapOf("telegram_dense" to COMPACT_MESSENGER)
    }
}

object KrakenThemePresetCatalog {
    fun options(selected: KrakenThemePreset): List<KrakenThemePresetOption> =
        workspacePresets.map { preset ->
            KrakenThemePresetOption(
                preset = preset,
                selected = preset == selected,
            )
        }

    fun diagnosticOptions(selected: KrakenThemePreset): List<KrakenThemePresetOption> =
        listOf(KrakenThemePreset.RESEARCH_CONSOLE).map { preset ->
            KrakenThemePresetOption(
                preset = preset,
                selected = preset == selected,
            )
        }

    private val workspacePresets: List<KrakenThemePreset> =
        KrakenThemePreset.entries.filterNot { it == KrakenThemePreset.RESEARCH_CONSOLE }
}
