package com.disser.kraken.identity

import java.security.SecureRandom
import java.util.Base64

interface IdentityKeyProvider {
    fun generateIdentityKeypair(): IdentityKeypair
}

class SecureRandomPlaceholderIdentityKeyProvider(
    private val secureRandom: SecureRandom = SecureRandom(),
) : IdentityKeyProvider {
    override fun generateIdentityKeypair(): IdentityKeypair {
        val publicKeyBytes = ByteArray(KEY_BYTES)
        val privateReferenceBytes = ByteArray(REFERENCE_BYTES)
        secureRandom.nextBytes(publicKeyBytes)
        secureRandom.nextBytes(privateReferenceBytes)

        return IdentityKeypair(
            publicKeyEncoded = "placeholder-pub:${publicKeyBytes.toBase64Url()}",
            privateKeyReference = "placeholder-private-ref:${privateReferenceBytes.toBase64Url()}",
        )
    }

    private fun ByteArray.toBase64Url(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    private companion object {
        const val KEY_BYTES = 32
        const val REFERENCE_BYTES = 24
    }
}
