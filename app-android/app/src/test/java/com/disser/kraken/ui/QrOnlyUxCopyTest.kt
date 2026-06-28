package com.disser.kraken.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class QrOnlyUxCopyTest {
    @Test
    fun primaryQrScreensDoNotExposeJsonExchangeCopy() {
        val sourceFiles = listOf(
            "src/main/java/com/disser/kraken/ui/screens/MyQrScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ImportInviteScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/ContactsScreen.kt",
            "src/main/java/com/disser/kraken/qr/QrScannerScreen.kt",
        )
        val forbiddenCopy = listOf(
            "Копировать JSON",
            "Invite JSON",
            "Вставить JSON",
            "Вставьте invite JSON",
            "ручная вставка JSON",
            "JSON copied",
            "JSON скопирован",
        )

        val joinedSource = sourceFiles.joinToString(separator = "\n") { path ->
            File(path).readText()
        }

        forbiddenCopy.forEach { phrase ->
            assertFalse("Primary QR UX must not expose '$phrase'", joinedSource.contains(phrase))
        }
    }

    @Test
    fun primaryQrScreensDoNotExposeRawDebugCopy() {
        val sourceFiles = listOf(
            "src/main/java/com/disser/kraken/ui/screens/ContactsScreen.kt",
            "src/main/java/com/disser/kraken/qr/QrScannerScreen.kt",
        )
        val forbiddenCopy = listOf(
            "Unknown peer",
            "QR приглашения",
            "pending.state.name",
        )

        val joinedSource = sourceFiles.joinToString(separator = "\n") { path ->
            File(path).readText()
        }

        forbiddenCopy.forEach { phrase ->
            assertFalse("Primary QR UX must not expose raw/debug copy '$phrase'", joinedSource.contains(phrase))
        }
    }

    @Test
    fun adminAndChatScreensDoNotExposeRawInternalLabels() {
        val sourceFiles = listOf(
            "src/main/java/com/disser/kraken/ui/screens/ChatScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/PendingApprovalsScreen.kt",
            "src/main/java/com/disser/kraken/ui/screens/RealmsScreen.kt",
        )
        val forbiddenCopy = listOf(
            "reason.name",
            "Recommended local action",
            "Category:",
            "Count:",
            "Target:",
            "Выданных invite",
            "invite edges",
            "debug-диагностике",
            "Membership certificate",
            "Pending requests",
            "Realm ID",
            "Issued by",
            "Read receipts default",
        )

        val joinedSource = sourceFiles.joinToString(separator = "\n") { path ->
            File(path).readText()
        }

        forbiddenCopy.forEach { phrase ->
            assertFalse("Admin/chat UI must not expose raw internal copy '$phrase'", joinedSource.contains(phrase))
        }
    }
}
