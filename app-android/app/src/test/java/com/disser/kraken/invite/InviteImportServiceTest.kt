package com.disser.kraken.invite

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InviteScope.REALM_MEMBERSHIP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteImportServiceTest {
    private val localIdentity = LocalIdentity(
        identityId = "local",
        displayName = "Alice",
        publicKeyEncoded = "placeholder-pub:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo",
        privateKeyReference = "placeholder-private-ref:local",
        fingerprint = FingerprintFormatter.shortFingerprint("placeholder-pub:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo"),
        createdAtEpochMillis = 1_700_000_000_000,
    )

    private val remotePayload = OneTimeInvitePayload(
        inviteId = "invite-1",
        inviterDisplayName = "Bob",
        inviterPublicKeyEncoded = "placeholder-pub:QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI",
        inviterFingerprint = "AB12 CD34 EF56 7890",
        createdAtEpochMillis = 1_700_000_000_100,
        expiresAtEpochMillis = null,
        capabilities = listOf("kraken.invite.v1"),
    )

    @Test
    fun exportedInviteJsonParsesBackToSameModel() {
        val encoded = InvitePayloadCodec.encode(remotePayload)
        val decoded = InvitePayloadCodec.decode(encoded).getOrThrow()

        assertEquals(remotePayload, decoded)
    }

    @Test
    fun invalidJsonIsRejected() {
        val result = InviteImportService().import("{not-json", localIdentity, emptyList())

        assertTrue(result is InviteImportResult.Error)
    }

    @Test
    fun missingRequiredFieldsAreRejected() {
        val result = InviteImportService().import("""{"type":"one_time_invite","version":1}""", localIdentity, emptyList())

        assertTrue(result is InviteImportResult.Error)
    }

    @Test
    fun selfInviteIsRejected() {
        val selfInvite = remotePayload.copy(inviterPublicKeyEncoded = localIdentity.publicKeyEncoded)

        val result = InviteImportService().import(
            InvitePayloadCodec.encode(selfInvite),
            localIdentity,
            emptyList(),
        )

        assertTrue(result is InviteImportResult.Error)
        assertEquals("Нельзя добавить собственный QR.", (result as InviteImportResult.Error).reason)
    }

    @Test
    fun duplicateInviteIdIsRejected() {
        val existing = PendingInviteImport(
            localId = "pending-1",
            inviteId = remotePayload.inviteId,
            inviterDisplayName = "Someone",
            inviterPublicKeyEncoded = "placeholder-pub:other",
            inviterFingerprint = "FFFF EEEE DDDD CCCC",
            importedAtEpochMillis = 1_700_000_000_200,
            state = PendingInviteState.PENDING_IMPORT,
        )

        val result = InviteImportService().import(
            InvitePayloadCodec.encode(remotePayload),
            localIdentity,
            listOf(existing),
        )

        assertTrue(result is InviteImportResult.Error)
        assertEquals("Приглашение уже добавлено.", (result as InviteImportResult.Error).reason)
    }

    @Test
    fun duplicatePublicKeyIsRejected() {
        val existing = PendingInviteImport(
            localId = "pending-1",
            inviteId = "invite-other",
            inviterDisplayName = "Bob",
            inviterPublicKeyEncoded = remotePayload.inviterPublicKeyEncoded,
            inviterFingerprint = remotePayload.inviterFingerprint,
            importedAtEpochMillis = 1_700_000_000_200,
            state = PendingInviteState.PENDING_IMPORT,
        )

        val result = InviteImportService().import(
            InvitePayloadCodec.encode(remotePayload),
            localIdentity,
            listOf(existing),
        )

        assertTrue(result is InviteImportResult.Error)
        assertEquals("Ключ уже знаком", (result as InviteImportResult.Error).reason)
    }

    @Test
    fun importCreatesPendingStateNotActiveRelationship() {
        val result = InviteImportService().import(
            InvitePayloadCodec.encode(remotePayload),
            localIdentity,
            emptyList(),
        )

        assertTrue(result is InviteImportResult.Success)
        val success = result as InviteImportResult.Success
        assertEquals(PendingInviteState.PENDING_IMPORT, success.pendingImport.state)
        assertEquals(remotePayload.inviteId, success.pendingImport.inviteId)
    }

    @Test
    fun realmInviteImportPreservesRealmScopeForHandshake() {
        val realmPayload = remotePayload.copy(
            scope = REALM_MEMBERSHIP,
            realmId = "realm-1",
            realmName = "OctoLab",
            requiresApproval = true,
        )

        val result = InviteImportService().import(
            InvitePayloadCodec.encode(realmPayload),
            localIdentity,
            emptyList(),
        )

        assertTrue(result is InviteImportResult.Success)
        val pending = (result as InviteImportResult.Success).pendingImport
        assertEquals(REALM_MEMBERSHIP, pending.scope)
        assertEquals("realm-1", pending.realmId)
        assertEquals("OctoLab", pending.realmName)
        assertTrue(pending.requiresApproval)
    }
}
