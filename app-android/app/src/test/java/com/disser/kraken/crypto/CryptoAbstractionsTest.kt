package com.disser.kraken.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoAbstractionsTest {
    @Test
    fun fakeCryptoBoxRoundTripsForRecipientInTestsOnly() {
        val box = TestOnlyCryptoBox()
        val plaintext = Plaintext("hello".encodeToByteArray())
        val recipient = PublicKeyMaterial("recipient-key")

        val ciphertext = box.encrypt(plaintext, recipient)
        val opened = box.decrypt(
            ciphertext = ciphertext,
            privateKeyReference = PrivateKeyReference("recipient-private-ref"),
            localPublicKey = recipient,
        )

        assertArrayEquals(plaintext.bytes, opened.bytes)
        assertEquals("test-only-no-production-crypto", ciphertext.algorithm)
    }

    @Test
    fun fakeCryptoBoxFailsForWrongRecipient() {
        val box = TestOnlyCryptoBox()
        val ciphertext = box.encrypt(
            plaintext = Plaintext("hello".encodeToByteArray()),
            recipientPublicKey = PublicKeyMaterial("recipient-key"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            box.decrypt(
                ciphertext = ciphertext,
                privateKeyReference = PrivateKeyReference("other-private-ref"),
                localPublicKey = PublicKeyMaterial("other-key"),
            )
        }
    }

    @Test
    fun candidatePrimitiveWarningRequiresReviewedLibraryLater() {
        assertEquals("X25519 or reviewed equivalent", CandidateCryptoPrimitives.KEY_AGREEMENT)
        assertEquals("HKDF", CandidateCryptoPrimitives.KDF)
        assertEquals(
            "Adamova admission decision bound into AEAD associated data",
            CandidateCryptoPrimitives.ADAMOVA_CONTEXT_BINDING,
        )
        assertEquals("Platform secure randomness", CandidateCryptoPrimitives.RANDOMNESS)
        assert(CandidateCryptoPrimitives.WARNING.contains("Reviewed crypto library"))
        assert(CandidateCryptoPrimitives.WARNING.contains("Adamova policy binding is mandatory"))
    }
}

private class TestOnlyCryptoBox : CryptoBox {
    override fun encrypt(
        plaintext: Plaintext,
        recipientPublicKey: PublicKeyMaterial,
    ): Ciphertext =
        Ciphertext(
            bytes = plaintext.bytes.reversedArray(),
            recipientPublicKey = recipientPublicKey.encoded,
            algorithm = "test-only-no-production-crypto",
        )

    override fun decrypt(
        ciphertext: Ciphertext,
        privateKeyReference: PrivateKeyReference,
        localPublicKey: PublicKeyMaterial,
    ): Plaintext {
        require(ciphertext.recipientPublicKey == localPublicKey.encoded) {
            "Wrong recipient"
        }
        return Plaintext(ciphertext.bytes.reversedArray())
    }
}
