package com.disser.kraken.crypto

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoPlanDocTest {
    @Test
    fun cryptoDocsPreserveCryptoScopeBoundary() {
        val envelope = File("../../protocol-spec/crypto-envelope.md").readText()
        val plan = File("../../docs/kraken-crypto-implementation-plan.md").readText()
        val keystore = File("../../docs/android-keystore-migration-plan.md").readText()

        listOf(
            "точек интеграции Android",
            "debug plaintext compatibility path",
            "blocked for release/prod",
        ).forEach { required -> assertTrue(envelope.contains(required)) }

        listOf(
            "DebugPlaintextPacketCrypto",
            "Обычный runtime `MeshService` теперь запускает message path в режиме",
            "Подпись сообщений вынесена в отдельный следующий слой",
            "Kraken полностью закрывает все промышленные сценарии защиты",
        ).forEach { required -> assertTrue(plan.contains(required)) }

        listOf(
            "migration plan",
            "AndroidKeystoreIdentityKeyProvider",
            "финальные security-claims фиксируются после review",
        ).forEach { required -> assertTrue(keystore.contains(required)) }
    }
}
