package com.disser.kraken.qr

import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.invite.OneTimeInvitePayload
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KrakenQrPayloadCodecTest {
    private val invitePayload = OneTimeInvitePayload(
        inviteId = "invite-deep-link",
        inviterDisplayName = "Alice",
        inviterPublicKeyEncoded = "placeholder-pub:alice",
        inviterFingerprint = "ABCD EFGH",
        createdAtEpochMillis = 1_700_000_000_000,
        expiresAtEpochMillis = null,
        capabilities = listOf("kraken.invite.v1"),
    )

    @Test
    fun encodePayloadCreatesCameraFriendlyKrakenQrUri() {
        val uri = KrakenQrPayloadCodec.encodePayload(InvitePayloadCodec.encode(invitePayload)).getOrThrow()

        assertTrue(uri.startsWith("kraken://qr?"))
        assertTrue(uri.contains("&z=d&"))
        assertTrue(uri.contains("&p="))
        assertTrue(KrakenQrPayloadCodec.isSupportedUri(uri))
    }

    @Test
    fun normalizeScannedTextDecodesKrakenQrUriToPayloadJson() {
        val uri = KrakenQrPayloadCodec.encodePayload(InvitePayloadCodec.encode(invitePayload)).getOrThrow()
        val normalized = KrakenQrPayloadCodec.normalizeScannedText(uri).getOrThrow()
        val decoded = InvitePayloadCodec.decode(normalized).getOrThrow()

        assertEquals(invitePayload, decoded)
        assertFalse(normalized.contains("\n"))
    }

    @Test
    fun normalizeScannedTextDecodesParsedKrakenIntentDataUri() {
        val krakenDataUri = KrakenQrPayloadCodec.encodePayload(InvitePayloadCodec.encode(invitePayload)).getOrThrow()

        val normalized = KrakenQrPayloadCodec.normalizeScannedText(krakenDataUri).getOrThrow()
        val decoded = InvitePayloadCodec.decode(normalized).getOrThrow()

        assertEquals(invitePayload, decoded)
        assertTrue(KrakenQrPayloadCodec.isSupportedUri(krakenDataUri))
    }

    @Test
    fun normalizeScannedTextKeepsPreviousAndroidIntentUriCompatible() {
        val krakenUri = KrakenQrPayloadCodec.encodePayload(InvitePayloadCodec.encode(invitePayload)).getOrThrow()
        val intentUri = krakenUri
            .replaceFirst("kraken://", "intent://")
            .replace("&z=d&", "&z=deflate&")
            .replace("&p=", "&payload=") + "#Intent;scheme=kraken;package=com.disser.kraken;end"

        val normalized = KrakenQrPayloadCodec.normalizeScannedText(intentUri).getOrThrow()
        val decoded = InvitePayloadCodec.decode(normalized).getOrThrow()

        assertEquals(invitePayload, decoded)
        assertTrue(KrakenQrPayloadCodec.isSupportedUri(intentUri))
    }

    @Test
    fun normalizeScannedTextKeepsHttpsQrUriCompatible() {
        val krakenUri = KrakenQrPayloadCodec.encodePayload(InvitePayloadCodec.encode(invitePayload)).getOrThrow()
        val httpsUri = krakenUri.replaceFirst("kraken://qr", "https://kraken.local/qr")

        val normalized = KrakenQrPayloadCodec.normalizeScannedText(httpsUri).getOrThrow()
        val decoded = InvitePayloadCodec.decode(normalized).getOrThrow()

        assertEquals(invitePayload, decoded)
        assertTrue(KrakenQrPayloadCodec.isSupportedUri(httpsUri))
    }

    @Test
    fun normalizeScannedTextKeepsLegacyKrakenUriCompatible() {
        val rawJson = InvitePayloadCodec.encode(invitePayload)
        val encodedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(rawJson.toByteArray(Charsets.UTF_8))
        val legacyUri = "kraken://qr?v=1&payload=$encodedPayload"

        val normalized = KrakenQrPayloadCodec.normalizeScannedText(legacyUri).getOrThrow()
        val decoded = InvitePayloadCodec.decode(normalized).getOrThrow()

        assertEquals(invitePayload, decoded)
        assertTrue(KrakenQrPayloadCodec.isSupportedUri(legacyUri))
    }

    @Test
    fun normalizeScannedTextKeepsRawJsonBackwardCompatible() {
        val rawJson = InvitePayloadCodec.encode(invitePayload)

        assertEquals(rawJson, KrakenQrPayloadCodec.normalizeScannedText("  $rawJson  ").getOrThrow())
    }

    @Test
    fun malformedKrakenQrUriFailsSafely() {
        val result = KrakenQrPayloadCodec.normalizeScannedText("kraken://qr?v=1")

        assertTrue(result.isFailure)
    }
}
