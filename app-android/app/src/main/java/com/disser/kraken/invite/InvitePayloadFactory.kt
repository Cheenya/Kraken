package com.disser.kraken.invite

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.crypto.DefaultCryptoProfileRegistry
import com.disser.kraken.crypto.KrakenCryptoProfile
import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.realm.Realm
import java.util.UUID

object InvitePayloadFactory {
    fun create(identity: LocalIdentity, nowEpochMillis: Long = System.currentTimeMillis()): OneTimeInvitePayload =
        createForProfile(
            identity = identity,
            cryptoProfile = DefaultCryptoProfileRegistry.standardProfile,
            nowEpochMillis = nowEpochMillis,
        )

    fun createForProfile(
        identity: LocalIdentity,
        cryptoProfile: KrakenCryptoProfile,
        admissionGate: ProductCryptoAdmissionGate = ProductCryptoAdmissionGate(),
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): OneTimeInvitePayload {
        val admission = admissionGate.evaluate(cryptoProfile)
        require(admission.acceptedForPacketPolicy) {
            "Experimental crypto profile rejected by Adamova admission gate: ${admission.decision}."
        }
        return OneTimeInvitePayload(
            inviteId = "invite-${UUID.randomUUID()}",
            scope = InviteScope.DIRECT_CONTACT,
            inviterDisplayName = identity.displayName,
            inviterPublicKeyEncoded = identity.publicKeyEncoded,
            inviterFingerprint = identity.fingerprint,
            createdAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + DEFAULT_EXPIRATION_MILLIS,
            oneTime = true,
            requiresHandshake = true,
            requiresApproval = false,
            nonce = "nonce-${UUID.randomUUID()}",
            capabilities = listOf("kraken.invite.v1", "kraken.relationship.v1"),
            cryptoProfileId = cryptoProfile.profileId,
            cryptoProfileHash = admission.profileHash,
            admissionDecisionHash = admission.decisionHash,
            profilePolicyVersion = admission.policyVersion,
            nativeBackendVersion = admission.nativeBackendVersion,
            signature = null,
        )
    }

    fun createRealmInvite(
        identity: LocalIdentity,
        realm: Realm,
        certificate: MembershipCertificate,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): OneTimeInvitePayload {
        require(certificate.realmId == realm.realmId) { "Certificate does not belong to realm." }
        require(certificate.memberPublicKey == identity.publicKeyEncoded) { "Certificate does not belong to local identity." }
        return OneTimeInvitePayload(
            inviteId = "invite-${UUID.randomUUID()}",
            scope = InviteScope.REALM_MEMBERSHIP,
            realmId = realm.realmId,
            realmName = realm.name,
            inviterDisplayName = identity.displayName,
            inviterPublicKeyEncoded = identity.publicKeyEncoded,
            inviterFingerprint = identity.fingerprint,
            createdAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + DEFAULT_EXPIRATION_MILLIS,
            oneTime = true,
            requiresHandshake = true,
            requiresApproval = true,
            nonce = "nonce-${UUID.randomUUID()}",
            capabilities = listOf(
                "kraken.invite.v1",
                "kraken.relationship.v1",
                "kraken.realm.membership.request.v1",
            ),
            cryptoProfileId = DefaultCryptoProfileRegistry.standardProfile.profileId,
            cryptoProfileHash = DefaultCryptoProfileRegistry.standardProfile.profileHash,
            admissionDecisionHash = KrakenCryptoProfileDefaults.STANDARD_ADMISSION_DECISION_HASH,
            profilePolicyVersion = KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION,
            nativeBackendVersion = KrakenCryptoProfileDefaults.STANDARD_NATIVE_BACKEND_VERSION,
            signature = null,
        )
    }

    private const val DEFAULT_EXPIRATION_MILLIS = 15 * 60 * 1000L
}
