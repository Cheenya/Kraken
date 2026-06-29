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
            "боевое шифрование",
            "боевой",
            "не боев",
            "в mesh",
            "через mesh",
            "QR-контакты для P2P",
            "ручной QR",
            "Ручной QR",
            "резервный QR",
            "ответный QR",
            "финальный QR",
            "QR-flow",
            "P2P работает",
            "Selected route",
            "Route attempts",
            "Queue retry",
            "Проверить отказы и retry",
            "path evidence",
            "receipt проверены",
            "отдельного smoke",
            "relay-прототип",
            "relay-маршрут",
            "Trust boundary",
            "Transport discovery",
            "LAN/Wi‑Fi active",
            "BLE active",
            "Wi‑Fi Direct active",
            "UIAuditSend",
            "Telegram-like",
            "Load demo",
            "Reset demo",
            "Активные контакты",
            "профиль на этом устройстве",
            "НА УСТРОЙСТВЕ",
            "на этом устройстве",
            "на вашем устройстве",
            "только на этом устройстве",
            "только с этого устройства",
            "пока только локально",
            "исследовательская проверка",
            "не боевая криптография",
            "боевым шифрованием",
            "Демонстрационный расчёт",
            "Запустить демонстрационный расчёт",
            "Ручной замер",
            "Проверьте invite",
            "Нужны новый invite",
            "получать backlog",
            "локального ревью",
            "На ревью",
            "админом",
            "админа",
            "state.name",
            "approvalPolicy.mode.name",
            "mesh не запущен",
            "Kraken mesh",
            "Kraken Mesh",
            "Kraken contact",
            "mesh-сообщениях",
            "Запуск mesh",
            "QR generation failed",
            "payload рукопожатия",
            "LAN endpoint",
            "ответного QR",
            "финального QR",
            "Отклонено неизвестных пиров",
            "решение проверки=",
            "принято=\${",
            "cyan-акцентами",
            "Статус: создано локально",
            "локальную личность",
            "Локальная личность",
            "fingerprints",
            "Fingerprint пира",
            "IP / host",
            "Text(\"Port\")",
            "прототипное подтверждение доставки",
            "рядом",
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
            "через ретрансляцию",
            "Wi‑Fi Direct:",
        ).forEach { phrase ->
            assertTrue("Route UI must include '$phrase'", routeSource.contains(phrase))
        }
    }

    @Test
    fun meshStopControlsRemainAvailableWhenForegroundServiceIsRunning() {
        val settingsSource = File("src/main/java/com/disser/kraken/ui/screens/SettingsScreen.kt").readText()
        val diagnosticsSource = File("src/main/java/com/disser/kraken/ui/screens/MeshStatusScreen.kt").readText()
        val serviceSource = File("src/main/java/com/disser/kraken/mesh/MeshForegroundService.kt").readText()
        val appStateSource = File("src/main/java/com/disser/kraken/navigation/KrakenAppState.kt").readText()
        val navHostSource = File("src/main/java/com/disser/kraken/navigation/KrakenNavHost.kt").readText()

        listOf(settingsSource, diagnosticsSource).forEach { source ->
            assertTrue(source.contains("val meshServiceActive = meshRunning || meshSnapshot.foregroundServiceEnabled"))
            assertTrue(source.contains("if (meshServiceActive)"))
            assertTrue(source.contains("onStopMesh"))
        }
        assertTrue(serviceSource.contains("PendingIntent.getForegroundService"))
        assertTrue(serviceSource.contains("ACTION_STOP_MESH"))
        assertTrue(serviceSource.contains("ACTION_SYNC_NOW -> {\n                if (!runtime.prefs.meshEnabled)"))
        assertTrue(serviceSource.contains("ACTION_REPLY ->"))
        assertTrue(appStateSource.contains("fun ensureMeshStarted(): Boolean"))
        assertTrue(appStateSource.contains("if (!meshRuntime.prefs.meshEnabled) return false"))
        assertTrue(appStateSource.contains("message-queued-network-stopped"))
        assertTrue(appStateSource.contains("message-retry-queued-network-stopped"))
        assertTrue(navHostSource.contains("val meshStartupAllowed = appState.ensureMeshStarted()"))
        assertTrue(navHostSource.contains("if (!meshStartupAllowed || launchWasRequested || meshIsStartingOrRunning)"))
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
        assertTrue(entrySource.contains("ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО"))
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
        assertTrue(source.contains("Подтверждаем локальный контакт"))
        assertTrue(source.contains("Завершаем локальное сопряжение"))
        assertTrue(source.contains("Дождитесь локального подтверждения. Если оно не придёт, используйте QR подтверждения."))
        assertTrue(source.contains("Не получилось через Bluetooth?"))
        assertFalse(source.contains("Остальные действия временно заблокированы"))
        assertFalse(source.contains("Ручное завершение второго устройства"))
        assertFalse(source.contains("исследовательская проверка"))
        assertFalse(source.contains("не боевая криптография"))
    }

    private companion object {
        val primaryUiSourceFiles = listOf(
            "src/main/java/com/disser/kraken/navigation/KrakenRoute.kt",
            "src/main/java/com/disser/kraken/navigation/KrakenAppState.kt",
            "src/main/java/com/disser/kraken/mesh/KrakenNotificationChannels.kt",
            "src/main/java/com/disser/kraken/mesh/MeshForegroundService.kt",
            "src/main/java/com/disser/kraken/mesh/KrakenMessageNotifier.kt",
            "src/main/java/com/disser/kraken/qr/QrScanResult.kt",
            "src/main/java/com/disser/kraken/ui/components/KrakenFormatters.kt",
            "src/main/java/com/disser/kraken/ui/components/PayloadQrCodeCard.kt",
            "src/main/java/com/disser/kraken/ui/components/TransportStatusBar.kt",
            "src/main/java/com/disser/kraken/ui/screens/WelcomeScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/CreateIdentityScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/HomeScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ChannelsScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ContactsScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ChatScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/SharedCards.kt",
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
            "src/main/java/com/disser/kraken/ui/theme/KrakenThemePreset.kt",
        )
    }
}
