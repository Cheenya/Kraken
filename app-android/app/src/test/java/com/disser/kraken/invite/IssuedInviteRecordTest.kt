package com.disser.kraken.invite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuedInviteRecordTest {
    @Test
    fun issuedRecordCopiesRealmInviteLifecycleFields() {
        val payload = OneTimeInvitePayload(
            inviteId = "invite-realm-1",
            scope = InviteScope.REALM_MEMBERSHIP,
            realmId = "realm-1",
            realmName = "OctoLab",
            inviterDisplayName = "Alice",
            inviterPublicKeyEncoded = "placeholder-pub:alice",
            inviterFingerprint = "ALICE-FP",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_086_400_000,
            requiresApproval = true,
            capabilities = listOf("realm_join_request"),
        )

        val record = IssuedInviteRecord.fromPayload(payload)

        assertEquals(payload.inviteId, record.inviteId)
        assertEquals(InviteScope.REALM_MEMBERSHIP, record.scope)
        assertEquals("realm-1", record.realmId)
        assertEquals("ALICE-FP", record.inviterFingerprint)
        assertEquals(1_700_086_400_000, record.expiresAtEpochMillis)
        assertEquals(payload, record.payload)
    }

    @Test
    fun knownLifecycleCarriesRevokedConsumedAndExpiry() {
        val record = IssuedInviteRecord(
            inviteId = "invite-realm-1",
            scope = InviteScope.REALM_MEMBERSHIP,
            realmId = "realm-1",
            inviterFingerprint = "ALICE-FP",
            createdAtEpochMillis = 1,
            expiresAtEpochMillis = 2,
            revoked = true,
            consumed = true,
            consumedAtEpochMillis = 3,
            consumedByPublicKey = "placeholder-pub:bob",
        )
        val lifecycle = record.toKnownInviteLifecycle()

        assertEquals("invite-realm-1", lifecycle.inviteId)
        assertTrue(lifecycle.revoked)
        assertTrue(lifecycle.consumed)
        assertEquals(2L, lifecycle.expiresAtEpochMillis)
        assertEquals(3L, record.consumedAtEpochMillis)
        assertEquals("placeholder-pub:bob", record.consumedByPublicKey)
    }
}
