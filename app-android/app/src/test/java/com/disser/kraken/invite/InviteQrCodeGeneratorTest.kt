package com.disser.kraken.invite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.CapacityState
import com.disser.kraken.realm.LocalRealmState
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmPolicy
import com.disser.kraken.qr.KrakenQrPayloadCodec

class InviteQrCodeGeneratorTest {
    private val payload = OneTimeInvitePayload(
        inviteId = "invite-test",
        inviterDisplayName = "Alice",
        inviterPublicKeyEncoded = "placeholder-pub:alice",
        inviterFingerprint = "ABCD EFGH",
        createdAtEpochMillis = 1_700_000_000_000,
        expiresAtEpochMillis = 1_700_000_900_000,
        capabilities = listOf("kraken.invite.v1"),
    )

    @Test
    fun qrGenerationFailsSafelyForBlankPayload() {
        val result = InviteQrCodeGenerator.generate("   ")

        assertTrue(result.isFailure)
    }

    @Test
    fun qrGenerationProducesStableMatrixForInviteJson() {
        val inviteJson = InvitePayloadCodec.encode(payload)
        val matrix = InviteQrCodeGenerator.generate(inviteJson, size = 160).getOrThrow()

        assertEquals(160, matrix.width)
        assertEquals(160, matrix.height)
        assertEquals(160 * 160, matrix.modules.size)
        assertTrue(matrix.modules.any { it })
        assertFalse(matrix.modules.all { it })
    }

    @Test
    fun displayModelKeepsPayloadAndInviteMetadata() {
        val inviteJson = InvitePayloadCodec.encode(payload)
        val model = InviteQrDisplayModelFactory.create(
            payload = payload,
            payloadJson = inviteJson,
            nowEpochMillis = payload.createdAtEpochMillis,
        ).getOrThrow()

        assertEquals(inviteJson, model.payloadJson)
        assertTrue(model.qrContent.startsWith("kraken://qr?"))
        assertTrue(KrakenQrPayloadCodec.isSupportedUri(model.qrContent))
        assertEquals(payload.inviteId, model.inviteId)
        assertEquals(payload.createdAtEpochMillis, model.createdAtEpochMillis)
        assertEquals(payload.expiresAtEpochMillis, model.expiresAtEpochMillis)
        assertEquals("ДОСТУПЕН", model.stateLabel)
        assertEquals("test", model.shortInviteId)
        assertEquals("15м 0с", model.expiresInLabel)
    }

    @Test
    fun lifecycleFormatterSupportsRevokedExpiredAndShortIds() {
        assertEquals("abcdefgh", InviteLifecycleFormatter.shortInviteId("invite-abcdefgh-1234"))
        assertEquals(
            InviteLifecycleState.REVOKED,
            InviteLifecycleFormatter.stateFor(payload, revoked = true, nowEpochMillis = payload.createdAtEpochMillis),
        )
        assertEquals(
            InviteLifecycleState.EXPIRED,
            InviteLifecycleFormatter.stateFor(
                payload,
                revoked = false,
                nowEpochMillis = payload.expiresAtEpochMillis ?: error("fixture expires"),
            ),
        )
        assertEquals(
            "Истёк",
            InviteLifecycleFormatter.expiresInLabel(payload.expiresAtEpochMillis, payload.expiresAtEpochMillis ?: 0),
        )
    }

    @Test
    fun realmInvitePayloadIncludesApprovalScopeAndRealmMetadata() {
        val identity = LocalIdentity(
            identityId = "alice",
            displayName = "Alice",
            publicKeyEncoded = "placeholder-pub:alice",
            privateKeyReference = "placeholder-private-ref:alice",
            fingerprint = "ALICE-FP",
            createdAtEpochMillis = 1_700_000_000_000,
        )
        val realm = Realm(
            realmId = "realm-1",
            name = "OctoLab",
            description = "Demo realm",
            createdByPublicKey = identity.publicKeyEncoded,
            createdAtEpochMillis = 1_700_000_000_000,
            policy = RealmPolicy(),
            capacityState = CapacityState(memberCount = 1, capacity = 500, epoch = 1_700_000_000_000),
            localState = LocalRealmState.ACTIVE,
        )
        val certificate = MembershipCertificate(
            realmId = realm.realmId,
            membershipId = "membership-owner",
            memberPublicKey = identity.publicKeyEncoded,
            issuedByPublicKey = identity.publicKeyEncoded,
            issuedAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = null,
            capabilities = listOf("admin"),
        )

        val realmInvite = InvitePayloadFactory.createRealmInvite(identity, realm, certificate)

        assertEquals(InviteScope.REALM_MEMBERSHIP, realmInvite.scope)
        assertEquals("realm-1", realmInvite.realmId)
        assertEquals("OctoLab", realmInvite.realmName)
        assertTrue(realmInvite.requiresHandshake)
        assertTrue(realmInvite.requiresApproval)
        assertTrue("kraken.realm.membership.request.v1" in realmInvite.capabilities)
    }
}
