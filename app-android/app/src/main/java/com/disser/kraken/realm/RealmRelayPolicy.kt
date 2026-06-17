package com.disser.kraken.realm

import com.disser.kraken.identity.LocalIdentity

enum class RealmRelayBlockReason {
    REALM_SNAPSHOT_MISSING,
    REALM_NOT_ACTIVE,
    REALM_TRANSIT_DISABLED,
    LOCAL_MEMBERSHIP_MISSING,
    LOCAL_MEMBERSHIP_RESTRICTED,
    LOCAL_RELAY_BASIC_MISSING,
    LOCAL_MEMBERSHIP_EXPIRED,
    RELAY_MEMBERSHIP_MISSING,
    RELAY_MEMBERSHIP_RESTRICTED,
    RELAY_BASIC_MISSING,
    RELAY_MEMBERSHIP_EXPIRED,
}

data class RealmRelayDecision(
    val allowed: Boolean,
    val blockReason: RealmRelayBlockReason? = null,
)

object RealmRelayPolicy {
    private const val RELAY_BASIC = "relay_basic"

    fun canUseRelayPeer(
        localIdentity: LocalIdentity,
        realmId: String,
        relayPeerPublicKey: String,
        realmSnapshot: RealmSnapshot?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): RealmRelayDecision {
        val snapshot = realmSnapshot
            ?: return RealmRelayDecision(false, RealmRelayBlockReason.REALM_SNAPSHOT_MISSING)
        val realm = snapshot.realms.firstOrNull { it.realmId == realmId }
            ?: return RealmRelayDecision(false, RealmRelayBlockReason.REALM_NOT_ACTIVE)
        if (realm.localState != LocalRealmState.ACTIVE) {
            return RealmRelayDecision(false, RealmRelayBlockReason.REALM_NOT_ACTIVE)
        }
        if (!realm.policy.allowTransit) {
            return RealmRelayDecision(false, RealmRelayBlockReason.REALM_TRANSIT_DISABLED)
        }

        val localCertificate = snapshot.membershipCertificates.firstOrNull {
            it.realmId == realmId && it.memberPublicKey == localIdentity.publicKeyEncoded
        } ?: return RealmRelayDecision(false, RealmRelayBlockReason.LOCAL_MEMBERSHIP_MISSING)
        localCertificate.blockReason(
            nowEpochMillis = nowEpochMillis,
            restrictedReason = RealmRelayBlockReason.LOCAL_MEMBERSHIP_RESTRICTED,
            relayMissingReason = RealmRelayBlockReason.LOCAL_RELAY_BASIC_MISSING,
            expiredReason = RealmRelayBlockReason.LOCAL_MEMBERSHIP_EXPIRED,
        )?.let { return RealmRelayDecision(false, it) }

        val relayCertificate = snapshot.membershipCertificates.firstOrNull {
            it.realmId == realmId && it.memberPublicKey == relayPeerPublicKey
        } ?: return RealmRelayDecision(false, RealmRelayBlockReason.RELAY_MEMBERSHIP_MISSING)
        relayCertificate.blockReason(
            nowEpochMillis = nowEpochMillis,
            restrictedReason = RealmRelayBlockReason.RELAY_MEMBERSHIP_RESTRICTED,
            relayMissingReason = RealmRelayBlockReason.RELAY_BASIC_MISSING,
            expiredReason = RealmRelayBlockReason.RELAY_MEMBERSHIP_EXPIRED,
        )?.let { return RealmRelayDecision(false, it) }

        return RealmRelayDecision(true)
    }

    fun blockReasonLabel(reason: RealmRelayBlockReason?): String =
        when (reason) {
            null -> "Ретрансляция разрешена"
            RealmRelayBlockReason.REALM_SNAPSHOT_MISSING -> "нет локального состояния реалма"
            RealmRelayBlockReason.REALM_NOT_ACTIVE -> "реалм не активен для ретрансляции"
            RealmRelayBlockReason.REALM_TRANSIT_DISABLED -> "в реалме отключена ретрансляция"
            RealmRelayBlockReason.LOCAL_MEMBERSHIP_MISSING -> "нет вашего сертификата участника"
            RealmRelayBlockReason.LOCAL_MEMBERSHIP_RESTRICTED -> "ваша роль ограничена"
            RealmRelayBlockReason.LOCAL_RELAY_BASIC_MISSING -> "у вас нет права relay_basic"
            RealmRelayBlockReason.LOCAL_MEMBERSHIP_EXPIRED -> "ваш сертификат участника истёк"
            RealmRelayBlockReason.RELAY_MEMBERSHIP_MISSING -> "узел не является участником реалма"
            RealmRelayBlockReason.RELAY_MEMBERSHIP_RESTRICTED -> "узел ограничен в реалме"
            RealmRelayBlockReason.RELAY_BASIC_MISSING -> "у узла нет права relay_basic"
            RealmRelayBlockReason.RELAY_MEMBERSHIP_EXPIRED -> "сертификат узла истёк"
        }

    private fun MembershipCertificate.blockReason(
        nowEpochMillis: Long,
        restrictedReason: RealmRelayBlockReason,
        relayMissingReason: RealmRelayBlockReason,
        expiredReason: RealmRelayBlockReason,
    ): RealmRelayBlockReason? =
        when {
            RealmManagementPolicy.isRestricted(this) -> restrictedReason
            RELAY_BASIC !in capabilities -> relayMissingReason
            expiresAtEpochMillis != null && expiresAtEpochMillis <= nowEpochMillis -> expiredReason
            else -> null
        }
}
