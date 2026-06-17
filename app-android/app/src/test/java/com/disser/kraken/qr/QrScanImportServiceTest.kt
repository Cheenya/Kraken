package com.disser.kraken.qr

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.invite.OneTimeInvitePayload
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.invite.PendingInviteState
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScanImportServiceTest {
    private val localIdentity = LocalIdentity(
        identityId = "local",
        displayName = "Alice",
        publicKeyEncoded = "placeholder-pub:local",
        privateKeyReference = "placeholder-private-ref:local",
        fingerprint = FingerprintFormatter.shortFingerprint("placeholder-pub:local"),
        createdAtEpochMillis = 1_700_000_000_000,
    )
    private val remotePayload = OneTimeInvitePayload(
        inviteId = "invite-qr",
        inviterDisplayName = "Bob",
        inviterPublicKeyEncoded = "placeholder-pub:remote",
        inviterFingerprint = "AB12 CD34 EF56 7890",
        createdAtEpochMillis = 1_700_000_000_100,
        expiresAtEpochMillis = null,
        capabilities = listOf("kraken.invite.v1"),
    )
    private val service = QrScanImportService()

    @Test
    fun decodedValidInviteJsonCreatesPendingImport() {
        val result = service.importScannedText(
            scannedText = InvitePayloadCodec.encode(remotePayload),
            localIdentity = localIdentity,
            existingImports = emptyList(),
        )

        assertTrue(result is QrScanImportResult.Success)
        val pendingImport = (result as QrScanImportResult.Success).pendingImport
        assertEquals(remotePayload.inviteId, pendingImport.inviteId)
        assertEquals(PendingInviteState.PENDING_IMPORT, pendingImport.state)
    }

    @Test
    fun decodedKrakenQrUriCreatesPendingImport() {
        val result = service.importScannedText(
            scannedText = KrakenQrPayloadCodec.encodePayload(InvitePayloadCodec.encode(remotePayload)).getOrThrow(),
            localIdentity = localIdentity,
            existingImports = emptyList(),
        )

        assertTrue(result is QrScanImportResult.Success)
        val pendingImport = (result as QrScanImportResult.Success).pendingImport
        assertEquals(remotePayload.inviteId, pendingImport.inviteId)
    }

    @Test
    fun invalidQrTextFailsSafely() {
        val result = service.importScannedText(
            scannedText = "not a Kraken invite",
            localIdentity = localIdentity,
            existingImports = emptyList(),
        )

        assertTrue(result is QrScanImportResult.Error)
        assertTrue((result as QrScanImportResult.Error).message.contains("не является корректным приглашением Kraken"))
    }

    @Test
    fun qrImportDoesNotCreateActiveRelationship() {
        val result = service.importScannedText(
            scannedText = InvitePayloadCodec.encode(remotePayload),
            localIdentity = localIdentity,
            existingImports = emptyList(),
        ) as QrScanImportResult.Success

        val relationship = RelationshipService.createFromPendingInvite(localIdentity, result.pendingImport)

        assertEquals(RelationshipState.PENDING_IMPORT, relationship.state)
    }

    @Test
    fun payloadKindDetectsInvalidQrSafely() {
        assertEquals(com.disser.kraken.handshake.HandshakePayloadKind.INVALID, com.disser.kraken.handshake.HandshakePayloadCodec.detectKind("not-json"))
    }

    @Test
    fun selfInviteStillUsesExistingValidation() {
        val selfInvite = remotePayload.copy(inviterPublicKeyEncoded = localIdentity.publicKeyEncoded)
        val result = service.importScannedText(
            scannedText = InvitePayloadCodec.encode(selfInvite),
            localIdentity = localIdentity,
            existingImports = emptyList(),
        )

        assertTrue(result is QrScanImportResult.Error)
        assertTrue((result as QrScanImportResult.Error).message.contains("Self-invite is not allowed"))
    }

    @Test
    fun duplicateInviteStillUsesExistingValidation() {
        val existing = PendingInviteImport(
            localId = "pending-existing",
            inviteId = remotePayload.inviteId,
            inviterDisplayName = remotePayload.inviterDisplayName,
            inviterPublicKeyEncoded = remotePayload.inviterPublicKeyEncoded,
            inviterFingerprint = remotePayload.inviterFingerprint,
            importedAtEpochMillis = 1_700_000_000_200,
            state = PendingInviteState.PENDING_IMPORT,
        )
        val result = service.importScannedText(
            scannedText = InvitePayloadCodec.encode(remotePayload),
            localIdentity = localIdentity,
            existingImports = listOf(existing),
        )

        assertTrue(result is QrScanImportResult.Error)
        assertTrue((result as QrScanImportResult.Error).message.contains("already imported"))
    }
}
