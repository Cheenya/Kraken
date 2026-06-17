package com.disser.kraken.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KrakenThemePresetTest {
    @Test
    fun defaultPresetIsKrakenDark() {
        assertEquals(KrakenThemePreset.KRAKEN_DARK, KrakenThemePreset.DEFAULT)
    }

    @Test
    fun invalidStoredPresetFallsBackToKrakenDark() {
        assertEquals(KrakenThemePreset.KRAKEN_DARK, KrakenThemePreset.fromStorageKey(null))
        assertEquals(KrakenThemePreset.KRAKEN_DARK, KrakenThemePreset.fromStorageKey("unknown"))
    }

    @Test
    fun catalogListsProductionPresetOptions() {
        val options = KrakenThemePresetCatalog.options(KrakenThemePreset.ABYSS)

        assertEquals(KrakenThemePreset.entries.size - 1, options.size)
        assertTrue(options.any { it.preset == KrakenThemePreset.LIQUID_GLASS })
        assertFalse(options.any { it.preset == KrakenThemePreset.RESEARCH_CONSOLE })
        assertEquals(1, options.count { it.selected })
        assertTrue(options.first { it.preset == KrakenThemePreset.ABYSS }.selected)
        assertFalse(options.first { it.preset == KrakenThemePreset.KRAKEN_DARK }.selected)
    }

    @Test
    fun diagnosticCatalogKeepsResearchConsoleSeparate() {
        val options = KrakenThemePresetCatalog.diagnosticOptions(KrakenThemePreset.RESEARCH_CONSOLE)

        assertEquals(1, options.size)
        assertEquals(KrakenThemePreset.RESEARCH_CONSOLE, options.single().preset)
        assertTrue(options.single().selected)
    }

    @Test
    fun presetStorageKeysAreStableAndUnique() {
        val keys = KrakenThemePreset.entries.map { it.storageKey }

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.contains("kraken_dark"))
        assertTrue(keys.contains("liquid_glass"))
        assertTrue(keys.contains("compact_messenger"))
        assertTrue(keys.contains("research_console"))
    }

    @Test
    fun legacyDenseMessengerStorageKeyStillLoadsCompactPreset() {
        assertEquals(KrakenThemePreset.COMPACT_MESSENGER, KrakenThemePreset.fromStorageKey("telegram_dense"))
    }

    @Test
    fun stylePresetsHaveDistinctLayoutTokens() {
        val defaultTokens = KrakenThemePreset.KRAKEN_DARK.tokens()
        val glassTokens = KrakenThemePreset.LIQUID_GLASS.tokens()
        val denseTokens = KrakenThemePreset.COMPACT_MESSENGER.tokens()
        val consoleTokens = KrakenThemePreset.RESEARCH_CONSOLE.tokens()

        assertTrue(glassTokens.cardRadius > defaultTokens.cardRadius)
        assertTrue(glassTokens.listRowMinHeight > defaultTokens.listRowMinHeight)
        assertTrue(denseTokens.cardSpacing < defaultTokens.cardSpacing)
        assertTrue(denseTokens.listRowMinHeight < defaultTokens.listRowMinHeight)
        assertEquals(KrakenSurfaceStyle.CONSOLE, consoleTokens.surfaceStyle)
        assertNotEquals(defaultTokens.colorScheme.primary, consoleTokens.colorScheme.primary)
    }

    @Test
    fun presetDisplayNamesAreKrakenProductNames() {
        val displayNames = KrakenThemePreset.entries.map { it.displayName.lowercase() }

        assertTrue(displayNames.none { it.contains("telegram") })
        assertTrue(displayNames.none { it.contains("signal") })
        assertTrue(displayNames.none { it.contains("research console") })
        assertTrue(displayNames.none { it.contains("quiet graphite") })
    }
}
