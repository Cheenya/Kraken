package com.disser.kraken.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiCopyGuardTest {
    @Test
    fun primaryScreensDoNotExposeOldDebugOrMisleadingMeshCopy() {
        val joinedSource = primaryUiSourceFiles.joinToString(separator = "\n") { path ->
            File(path).readText()
        }
        val forbiddenCopy = listOf(
            "Demo Active Peer",
            "Experimental UI. Not production flow.",
            "UI Lab",
            "P0 smoke",
            "Evidence JSON",
            "Debug evidence",
            "debug negative/retry evidence",
            "Production encryption",
            "production encryption не реализован",
            "production-криптографии",
            "production-шифрование",
            "в mesh",
            "через mesh",
            "QR-контакты для P2P",
            "ответный QR",
            "финальный QR",
            "P2P работает",
            "Selected route",
            "Route attempts",
            "Queue retry",
            "LAN/Wi‑Fi active",
            "BLE active",
            "Wi‑Fi Direct active",
            "UIAuditSend",
            "Telegram-like",
            "Load demo",
            "Reset demo",
            "Активные контакты",
        )

        forbiddenCopy.forEach { phrase ->
            assertFalse("Primary UI must not expose old/debug copy '$phrase'", joinedSource.contains(phrase))
        }
    }

    @Test
    fun routeLabelsStayExplicitAndNonPromissory() {
        val routeSource = File("src/main/java/com/disser/kraken/mesh/PeerRouteModels.kt").readText() +
            File("src/main/java/com/disser/kraken/ui/screens/TwoPhoneChecklistScreen.kt").readText()

        listOf(
            "Bluetooth напрямую",
            "Wi‑Fi/LAN напрямую",
            "нет маршрута",
            "через relay-прототип",
            "Wi‑Fi Direct активен",
        ).forEach { phrase ->
            assertTrue("Route UI must include '$phrase'", routeSource.contains(phrase))
        }
    }

    @Test
    fun homeAndWelcomePreserveBrandedEntryCopy() {
        val entrySource = listOf(
            "src/main/java/com/disser/kraken/navigation/KrakenRoute.kt",
            "src/main/java/com/disser/kraken/ui/screens/HomeScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/WelcomeScreen.kt",
        ).joinToString(separator = "\n") { path -> File(path).readText() }

        assertTrue(entrySource.contains("Главная"))
        assertTrue(entrySource.contains("ОБЗОР"))
        assertTrue(entrySource.contains("M E S S E N G E R"))
        assertTrue(entrySource.contains("ПРИВАТНО  •  РЯДОМ  •  СВОБОДНО"))
        assertFalse(entrySource.contains("ПРИВАТНО  •  QR  •  БЕЗ СЕРВЕРОВ"))
        assertFalse(entrySource.contains("ПРИВАТНО  •  ПО QR  •  БЕЗ СЕРВЕРОВ"))
        assertFalse(entrySource.contains("ПРИВАТНО  •  ОФФЛАЙН  •  ЛОКАЛЬНО"))
    }

    @Test
    fun contactManagementExposesLocalForgetPathForStalePairing() {
        val source = listOf(
            "src/main/java/com/disser/kraken/navigation/KrakenAppState.kt",
            "src/main/java/com/disser/kraken/ui/screens/ContactsScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/RelationshipHandshakeActions.kt",
            "src/main/java/com/disser/kraken/ui/screens/ChatScreen.kt",
        ).joinToString(separator = "\n") { path -> File(path).readText() }

        assertTrue(source.contains("Забыть устройство"))
        assertTrue(source.contains("Профиль"))
        assertTrue(source.contains("relationship-forgotten-locally"))
        assertTrue(source.contains("Отменить сопряжение"))
        assertTrue(source.contains("Подтверждаем контакт рядом"))
        assertTrue(source.contains("Завершаем сопряжение рядом"))
        assertTrue(source.contains("Оставьте устройства рядом. Если подтверждение не придёт, используйте резервный QR."))
        assertTrue(source.contains("Не получилось через Bluetooth?"))
        assertFalse(source.contains("Остальные действия временно заблокированы"))
        assertFalse(source.contains("Ручное завершение второго устройства"))
    }

    private companion object {
        val primaryUiSourceFiles = listOf(
            "src/main/java/com/disser/kraken/navigation/KrakenRoute.kt",
            "src/main/java/com/disser/kraken/ui/screens/WelcomeScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/CreateIdentityScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/HomeScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ContactsScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ChatScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/MyQrScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ImportInviteScreen.kt",
            "src/main/java/com/disser/kraken/qr/QrScannerScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/MeshStatusScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/SettingsScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ResearchScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/TwoPhoneChecklistScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/experimental/UiLabScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/experimental/IconLabScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/experimental/HomeUxVariants.kt",
            "src/main/java/com/disser/kraken/ui/screens/experimental/ChatUxVariants.kt",
        )
    }
}
