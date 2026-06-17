package com.disser.kraken.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KrakenRouteTest {
    @Test
    fun bottomNavigationIsMessengerFirst() {
        assertEquals(
            listOf(
                KrakenRoute.Chat,
                KrakenRoute.Contacts,
                KrakenRoute.Realms,
                KrakenRoute.Settings,
            ),
            KrakenRoute.bottomRoutes,
        )
        assertFalse(KrakenRoute.MyQr in KrakenRoute.bottomRoutes)
        assertFalse(KrakenRoute.Home in KrakenRoute.bottomRoutes)
        assertFalse(KrakenRoute.Research in KrakenRoute.bottomRoutes)
        assertTrue(KrakenRoute.Contacts in KrakenRoute.bottomRoutes)
        assertTrue(KrakenRoute.bottomRoutes.all { it.bottomNav })
        assertEquals(KrakenRoute.entries.filter { it.bottomNav }.toSet(), KrakenRoute.bottomRoutes.toSet())
    }

    @Test
    fun legacyHomeRootStillShowsMessengerBottomMenu() {
        val bottomBarSource = java.io.File("src/main/java/com/disser/kraken/ui/components/KrakenBottomBar.kt").readText()

        assertTrue(bottomBarSource.contains("currentRoute == KrakenRoute.Home"))
        assertFalse(KrakenRoute.Home in KrakenRoute.bottomRoutes)
    }
}
