package com.disser.kraken.crypto

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoPlanDocTest {
    @Test
    fun cryptoDocsPreservePrototypeBoundary() {
        val envelope = File("../../protocol-spec/crypto-envelope.md").readText()
        val plan = File("../../docs/kraken-crypto-implementation-plan.md").readText()
        val keystore = File("../../docs/android-keystore-migration-plan.md").readText()

        listOf(
            "Production cryptography is not implemented",
            "not encryption",
            "blocked for release/prod",
        ).forEach { required -> assertTrue(envelope.contains(required)) }

        listOf(
            "PrototypeNoSecurityPacketCrypto",
            "Обычный runtime `MeshService` теперь запускает message path в режиме",
            "Реальная подпись сообщений ещё не реализована",
            "Kraken уже является промышленно защищённым мессенджером",
        ).forEach { required -> assertTrue(plan.contains(required)) }

        listOf(
            "plan only",
            "AndroidKeystoreIdentityKeyProvider",
            "no production security claim before review",
        ).forEach { required -> assertTrue(keystore.contains(required)) }
    }
}
