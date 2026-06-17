package com.disser.kraken.ui

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeBrandingGuardTest {
    @Test
    fun appStartsAtBrandedWelcomeInsteadOfBypassingToChats() {
        val navHostSource = File("src/main/java/com/disser/kraken/navigation/KrakenNavHost.kt").readText()

        assertTrue(navHostSource.contains("val startDestination = KrakenRoute.Welcome.route"))
        assertFalse(navHostSource.contains("val startDestination = if (appState.localIdentity != null)"))
    }

    @Test
    fun brandedLaunchWaitsForAppReadinessInsteadOfTimeout() {
        val launchSource = File("src/main/java/com/disser/kraken/ui/screens/BrandedLaunchScreen.kt").readText()
        val navHostSource = File("src/main/java/com/disser/kraken/navigation/KrakenNavHost.kt").readText()

        assertTrue(launchSource.contains("launchReady: Boolean = false"))
        assertTrue(navHostSource.contains("onLaunchReady()"))
        assertFalse(launchSource.contains("delay("))
        assertFalse(launchSource.contains("BrandedLaunchMinimumMillis"))
    }

    @Test
    fun composeRefreshLoopDoesNotDuplicateForegroundMeshSync() {
        val navHostSource = File("src/main/java/com/disser/kraken/navigation/KrakenNavHost.kt").readText()

        assertTrue(navHostSource.contains("appState.refreshMeshSnapshot()"))
        assertFalse(navHostSource.contains("appState.syncMeshIfRunning()"))
    }

    @Test
    fun welcomeScreenKeepsMessengerBrandCopy() {
        val welcomeSource = File("src/main/java/com/disser/kraken/ui/screens/WelcomeScreen.kt").readText()

        assertTrue(welcomeSource.contains("M E S S E N G E R"))
        assertTrue(welcomeSource.contains("ПРИВАТНО  •  РЯДОМ  •  СВОБОДНО"))
        assertTrue(welcomeSource.contains("ОТКРЫТЬ KRAKEN"))
        assertTrue(welcomeSource.contains("route = KrakenRoute.Chat"))
        assertFalse(welcomeSource.contains("Л И Ч Н О  •  О Ф Л А Й Н  •  Л О К А Л Ь Н О"))
    }

    @Test
    fun splashAssetKeepsKrakenMessengerArtwork() {
        val splashAsset = File("src/main/res/drawable-nodpi/kraken_splash_dark.png")

        assertEquals(
            "20e98a5e0a03915efdba7ceceeda0665772750ba999e1f04cc65285aae191488",
            splashAsset.sha256(),
        )
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
