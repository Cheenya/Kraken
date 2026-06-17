package com.disser.kraken.realm

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService

enum class RealmCommunicationBlockReason {
    RELATIONSHIP_NOT_ACTIVE,
    REALM_SNAPSHOT_MISSING,
    REALM_NOT_ACTIVE,
    LOCAL_MEMBERSHIP_MISSING,
    LOCAL_MEMBERSHIP_RESTRICTED,
    LOCAL_SEND_DIRECT_MISSING,
    LOCAL_MEMBERSHIP_EXPIRED,
    PEER_MEMBERSHIP_MISSING,
    PEER_MEMBERSHIP_RESTRICTED,
    PEER_SEND_DIRECT_MISSING,
    PEER_MEMBERSHIP_EXPIRED,
}

data class RealmCommunicationDecision(
    val allowed: Boolean,
    val blockReason: RealmCommunicationBlockReason? = null,
)

object RealmCommunicationPolicy {
    private const val SEND_DIRECT = "send_direct"

    fun canUseRelationship(
        localIdentity: LocalIdentity,
        relationship: Relationship,
        realmSnapshot: RealmSnapshot?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): RealmCommunicationDecision {
        if (!RelationshipService.canSendMessage(relationship)) {
            return RealmCommunicationDecision(false, RealmCommunicationBlockReason.RELATIONSHIP_NOT_ACTIVE)
        }
        val realmId = relationship.realmId ?: return RealmCommunicationDecision(true)
        val snapshot = realmSnapshot
            ?: return RealmCommunicationDecision(false, RealmCommunicationBlockReason.REALM_SNAPSHOT_MISSING)
        val realm = snapshot.realms.firstOrNull { it.realmId == realmId }
            ?: return RealmCommunicationDecision(false, RealmCommunicationBlockReason.REALM_NOT_ACTIVE)
        if (realm.localState !in setOf(LocalRealmState.ACTIVE, LocalRealmState.ONLY_DIRECT)) {
            return RealmCommunicationDecision(false, RealmCommunicationBlockReason.REALM_NOT_ACTIVE)
        }

        val localCertificate = snapshot.membershipCertificates.firstOrNull {
            it.realmId == realmId && it.memberPublicKey == localIdentity.publicKeyEncoded
        } ?: return RealmCommunicationDecision(false, RealmCommunicationBlockReason.LOCAL_MEMBERSHIP_MISSING)
        localCertificate.blockReason(
            nowEpochMillis = nowEpochMillis,
            restrictedReason = RealmCommunicationBlockReason.LOCAL_MEMBERSHIP_RESTRICTED,
            sendDirectMissingReason = RealmCommunicationBlockReason.LOCAL_SEND_DIRECT_MISSING,
            expiredReason = RealmCommunicationBlockReason.LOCAL_MEMBERSHIP_EXPIRED,
        )?.let { return RealmCommunicationDecision(false, it) }

        val peerCertificate = snapshot.membershipCertificates.firstOrNull {
            it.realmId == realmId && it.memberPublicKey == relationship.peerPublicKey
        } ?: return RealmCommunicationDecision(false, RealmCommunicationBlockReason.PEER_MEMBERSHIP_MISSING)
        peerCertificate.blockReason(
            nowEpochMillis = nowEpochMillis,
            restrictedReason = RealmCommunicationBlockReason.PEER_MEMBERSHIP_RESTRICTED,
            sendDirectMissingReason = RealmCommunicationBlockReason.PEER_SEND_DIRECT_MISSING,
            expiredReason = RealmCommunicationBlockReason.PEER_MEMBERSHIP_EXPIRED,
        )?.let { return RealmCommunicationDecision(false, it) }

        return RealmCommunicationDecision(true)
    }

    fun blockReasonLabel(reason: RealmCommunicationBlockReason?): String =
        when (reason) {
            null -> "Разрешено"
            RealmCommunicationBlockReason.RELATIONSHIP_NOT_ACTIVE -> "контакт не ACTIVE"
            RealmCommunicationBlockReason.REALM_SNAPSHOT_MISSING -> "нет локального состояния реалма"
            RealmCommunicationBlockReason.REALM_NOT_ACTIVE -> "реалм не активен для переписки"
            RealmCommunicationBlockReason.LOCAL_MEMBERSHIP_MISSING -> "нет вашего сертификата участника"
            RealmCommunicationBlockReason.LOCAL_MEMBERSHIP_RESTRICTED -> "ваша роль ограничена"
            RealmCommunicationBlockReason.LOCAL_SEND_DIRECT_MISSING -> "у вас нет права send_direct"
            RealmCommunicationBlockReason.LOCAL_MEMBERSHIP_EXPIRED -> "ваш сертификат участника истёк"
            RealmCommunicationBlockReason.PEER_MEMBERSHIP_MISSING -> "участник удалён из реалма"
            RealmCommunicationBlockReason.PEER_MEMBERSHIP_RESTRICTED -> "участник ограничен в реалме"
            RealmCommunicationBlockReason.PEER_SEND_DIRECT_MISSING -> "у участника нет права send_direct"
            RealmCommunicationBlockReason.PEER_MEMBERSHIP_EXPIRED -> "сертификат участника истёк"
        }

    private fun MembershipCertificate.blockReason(
        nowEpochMillis: Long,
        restrictedReason: RealmCommunicationBlockReason,
        sendDirectMissingReason: RealmCommunicationBlockReason,
        expiredReason: RealmCommunicationBlockReason,
    ): RealmCommunicationBlockReason? =
        when {
            RealmManagementPolicy.isRestricted(this) -> restrictedReason
            SEND_DIRECT !in capabilities -> sendDirectMissingReason
            expiresAtEpochMillis != null && expiresAtEpochMillis <= nowEpochMillis -> expiredReason
            else -> null
        }
}
