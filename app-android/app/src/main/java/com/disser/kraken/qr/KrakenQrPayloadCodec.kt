package com.disser.kraken.qr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object KrakenQrPayloadCodec {
    const val SCHEME = "kraken"
    const val HOST = "qr"
    const val WEB_SCHEME = "https"
    const val WEB_HOST = "kraken.local"
    const val WEB_PATH = "/qr"
    const val ANDROID_INTENT_SCHEME = "intent"
    const val PACKAGE_NAME = "com.disser.kraken"
    private const val VERSION_QUERY = "v"
    private const val PAYLOAD_QUERY = "payload"
    private const val COMPACT_PAYLOAD_QUERY = "p"
    private const val COMPRESSION_QUERY = "z"
    private const val DEFLATE_COMPRESSION = "deflate"
    private const val COMPACT_DEFLATE_COMPRESSION = "d"
    private const val VERSION = "2"

    private val compactJson = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encodePayload(rawPayload: String): Result<String> = runCatching {
        val compactPayload = compactPayload(rawPayload)
        val compressedPayload = deflate(compactPayload.toByteArray(Charsets.UTF_8))
        val encodedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(compressedPayload)
        "$SCHEME://$HOST?$VERSION_QUERY=$VERSION&$COMPRESSION_QUERY=$COMPACT_DEFLATE_COMPRESSION&$COMPACT_PAYLOAD_QUERY=$encodedPayload"
    }

    fun normalizeScannedText(scannedText: String): Result<String> = runCatching {
        val trimmed = scannedText.trim()
        require(trimmed.isNotBlank()) { "QR payload is blank." }
        if (isSupportedUri(trimmed)) {
            decodeUri(trimmed)
        } else {
            trimmed
        }
    }

    fun isSupportedUri(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        return runCatching {
            val uri = URI(trimmed)
            when {
                uri.scheme.equals(SCHEME, ignoreCase = true) ->
                    uri.host.equals(HOST, ignoreCase = true)
                uri.scheme.equals(WEB_SCHEME, ignoreCase = true) ->
                    uri.host.equals(WEB_HOST, ignoreCase = true) &&
                        uri.path.equals(WEB_PATH, ignoreCase = true)
                uri.scheme.equals(ANDROID_INTENT_SCHEME, ignoreCase = true) ->
                    uri.host.equals(HOST, ignoreCase = true) &&
                        uri.rawFragment.orEmpty().contains("scheme=$SCHEME", ignoreCase = true)
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun decodeUri(uriText: String): String {
        val uri = URI(uriText)
        require(
            uri.scheme.equals(SCHEME, ignoreCase = true) ||
                uri.scheme.equals(WEB_SCHEME, ignoreCase = true) ||
                uri.scheme.equals(ANDROID_INTENT_SCHEME, ignoreCase = true),
        ) { "Unsupported QR scheme." }
        if (uri.scheme.equals(WEB_SCHEME, ignoreCase = true)) {
            require(uri.host.equals(WEB_HOST, ignoreCase = true)) { "Unsupported QR host." }
            require(uri.path.equals(WEB_PATH, ignoreCase = true)) { "Unsupported QR path." }
        } else {
            require(uri.host.equals(HOST, ignoreCase = true)) { "Unsupported QR host." }
        }
        if (uri.scheme.equals(ANDROID_INTENT_SCHEME, ignoreCase = true)) {
            require(uri.rawFragment.orEmpty().contains("scheme=$SCHEME", ignoreCase = true)) {
                "Unsupported QR intent target."
            }
        }
        val query = parseQuery(uri.rawQuery)
        val payload = query[COMPACT_PAYLOAD_QUERY] ?: query[PAYLOAD_QUERY]
            ?: throw IllegalArgumentException("Kraken QR payload is missing.")
        val decodedBytes = Base64.getUrlDecoder().decode(payload)
        val decoded = if (query[COMPRESSION_QUERY] in setOf(DEFLATE_COMPRESSION, COMPACT_DEFLATE_COMPRESSION)) {
            inflate(decodedBytes).toString(Charsets.UTF_8)
        } else {
            decodedBytes.toString(Charsets.UTF_8)
        }
        return compactPayload(decoded)
    }

    private fun compactPayload(rawPayload: String): String {
        val trimmed = rawPayload.trim()
        require(trimmed.isNotBlank()) { "QR payload is blank." }
        return try {
            val element = compactJson.parseToJsonElement(trimmed)
            compactJson.encodeToString(JsonElement.serializer(), element)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("Invalid Kraken QR payload.", error)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Kraken QR payload.", error)
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery
            .split("&")
            .mapNotNull { part ->
                val separatorIndex = part.indexOf("=")
                if (separatorIndex <= 0) return@mapNotNull null
                val key = part.substring(0, separatorIndex).decodeUrlComponent()
                val value = part.substring(separatorIndex + 1).decodeUrlComponent()
                key to value
            }
            .toMap()
    }

    private fun String.decodeUrlComponent(): String =
        URLDecoder.decode(this, Charsets.UTF_8.name())

    private fun deflate(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output, Deflater(Deflater.BEST_COMPRESSION, true)).use { stream ->
            stream.write(input)
        }
        return output.toByteArray()
    }

    private fun inflate(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(input), Inflater(true)).use { stream ->
            stream.copyTo(output)
        }
        return output.toByteArray()
    }
}
