package com.disser.kraken.invite

import com.disser.kraken.handshake.HandshakeResponsePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuedInviteLifecyclePolicyTest {
    @Test
    fun directResponseRequiresMatchingIssuedDirectInvite() {
        val invite = directInvite().let(IssuedInviteRecord.Companion::fromPayload)
        val response = directResponse(inviteId = invite.inviteId)

        val lifecycle = IssuedInviteLifecyclePolicy.knownLifecycleForResponse(
            payload = response,
            issuedInvites = listOf(invite),
            localFingerprint = "ALICE-FP",
        ).getOrThrow()

        assertEquals(invite.inviteId, lifecycle.inviteId)
    }

    @Test
    fun unknownDirectInviteFails() {
        val result = IssuedInviteLifecyclePolicy.knownLifecycleForResponse(
            payload = directResponse(inviteId = "invite-missing"),
            issuedInvites = emptyList(),
            localFingerprint = "ALICE-FP",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("неизвестное"))
    }

    @Test
    fun directResponseRejectsScopeMismatch() {
        val realmRecord = directInvite().let {
            IssuedInviteRecord.fromPayload(
                it.copy(
                    scope = InviteScope.REALM_MEMBERSHIP,
                    realmId = "realm-1",
                    requiresApproval = true,
                )
            )
        }

        val result = IssuedInviteLifecyclePolicy.knownLifecycleForResponse(
            payload = directResponse(inviteId = realmRecord.inviteId),
            issuedInvites = listOf(realmRecord),
            localFingerprint = "ALICE-FP",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("другому типу"))
    }

    @Test
    fun directLifecycleCarriesRevokedConsumedAndExpiry() {
        val invite = directInvite().let {
            IssuedInviteRecord.fromPayload(it).copy(
                revoked = true,
                consumed = true,
                expiresAtEpochMillis = 2_000,
            )
        }

        val lifecycle = IssuedInviteLifecyclePolicy.knownLifecycleForResponse(
            payload = directResponse(inviteId = invite.inviteId),
            issuedInvites = listOf(invite),
            localFingerprint = "ALICE-FP",
        ).getOrThrow()

        assertTrue(lifecycle.revoked)
        assertTrue(lifecycle.consumed)
        assertEquals(2_000L, lifecycle.expiresAtEpochMillis)
    }

    @Test
    fun realmResponseRequiresMatchingRealmInvite() {
        val invite = directInvite().copy(
            scope = InviteScope.REALM_MEMBERSHIP,
            realmId = "realm-1",
            requiresApproval = true,
        ).let(IssuedInviteRecord.Companion::fromPayload)
        val response = directResponse(inviteId = invite.inviteId).copy(
            realmId = "realm-1",
            requiresApproval = true,
        )

        val lifecycle = IssuedInviteLifecyclePolicy.knownLifecycleForResponse(
            payload = response,
            issuedInvites = listOf(invite),
            localFingerprint = "ALICE-FP",
        ).getOrThrow()

        assertEquals(invite.inviteId, lifecycle.inviteId)
    }

    private fun directInvite(): OneTimeInvitePayload =
        OneTimeInvitePayload(
            inviteId = "invite-direct-1",
            scope = InviteScope.DIRECT_CONTACT,
            inviterDisplayName = "Alice",
            inviterPublicKeyEncoded = "placeholder-pub:alice",
            inviterFingerprint = "ALICE-FP",
            createdAtEpochMillis = 1_000,
            expiresAtEpochMillis = 2_000,
            capabilities = listOf("kraken.invite.v1", "kraken.relationship.v1"),
        )

    private fun directResponse(inviteId: String): HandshakeResponsePayload =
        HandshakeResponsePayload(
            responseId = "response-1",
            inviteId = inviteId,
            responderFingerprint = "BOB-FP",
            responderDisplayName = "Bob",
            responderPublicKeyEncoded = "placeholder-pub:bob",
            inviterFingerprint = "ALICE-FP",
            createdAtEpochMillis = 1_100,
            relationshipHint = "relationship-1",
        )
}
