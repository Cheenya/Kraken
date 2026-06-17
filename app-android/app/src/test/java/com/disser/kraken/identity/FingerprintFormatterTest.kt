package com.disser.kraken.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintFormatterTest {
    @Test
    fun fingerprintIsStableForSamePublicKey() {
        val publicKey = "placeholder-pub:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo"

        assertEquals(
            FingerprintFormatter.shortFingerprint(publicKey),
            FingerprintFormatter.shortFingerprint(publicKey),
        )
        assertEquals(
            FingerprintFormatter.fullFingerprint(publicKey),
            FingerprintFormatter.fullFingerprint(publicKey),
        )
    }

    @Test
    fun displayNameChangeDoesNotChangeFingerprintOrPublicKey() {
        val identity = LocalIdentity(
            identityId = "identity-1",
            displayName = "Alice",
            publicKeyEncoded = "placeholder-pub:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo",
            privateKeyReference = "placeholder-private-ref:ref",
            fingerprint = FingerprintFormatter.shortFingerprint(
                "placeholder-pub:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo"
            ),
            createdAtEpochMillis = 1_700_000_000_000,
        )

        val renamed = identity.copy(displayName = "Alice Updated")

        assertEquals(identity.publicKeyEncoded, renamed.publicKeyEncoded)
        assertEquals(identity.privateKeyReference, renamed.privateKeyReference)
        assertEquals(identity.fingerprint, renamed.fingerprint)
        assertEquals("Alice Updated", renamed.displayName)
    }

    @Test
    fun identityModelDoesNotExposeForbiddenDeviceOrAccountFields() {
        val forbidden = setOf(
            "phone",
            "phone_number",
            "email",
            "login",
            "password",
            "imei",
            "android_id",
            "device_id",
            "mac",
            "serial",
            "hardware_fingerprint",
        )

        val fieldNames = LocalIdentity::class.java.declaredFields.map { it.name.lowercase() }.toSet()

        assertTrue(fieldNames.isNotEmpty())
        assertTrue(fieldNames.intersect(forbidden).isEmpty())
    }

    @Test
    fun placeholderProviderCreatesDifferentKeys() {
        val provider = SecureRandomPlaceholderIdentityKeyProvider()

        val first = provider.generateIdentityKeypair()
        val second = provider.generateIdentityKeypair()

        assertFalse(first.publicKeyEncoded == second.publicKeyEncoded)
        assertTrue(first.publicKeyEncoded.startsWith("placeholder-pub:"))
        assertTrue(first.privateKeyReference.startsWith("placeholder-private-ref:"))
    }
}
