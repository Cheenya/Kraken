package com.disser.kraken.identity

import java.security.MessageDigest
import java.util.Base64

object FingerprintFormatter {
    fun fullFingerprint(publicKeyEncoded: String): String {
        val keyBytes = decodePublicKeyMaterial(publicKeyEncoded)
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        return digest.joinToString(separator = "") { byte -> "%02X".format(byte) }
    }

    fun shortFingerprint(publicKeyEncoded: String): String =
        fullFingerprint(publicKeyEncoded)
            .take(16)
            .chunked(4)
            .joinToString(" ")

    private fun decodePublicKeyMaterial(publicKeyEncoded: String): ByteArray {
        val encodedPart = publicKeyEncoded.substringAfter(":", missingDelimiterValue = publicKeyEncoded)
        return runCatching {
            Base64.getUrlDecoder().decode(encodedPart)
        }.getOrElse {
            publicKeyEncoded.toByteArray(Charsets.UTF_8)
        }
    }
}
