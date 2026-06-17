package com.disser.kraken.relationship

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.PendingInviteImport
import java.util.UUID
object RelationshipService {
    fun createFromPendingInvite(
        localIdentity: LocalIdentity,
        pendingInvite: PendingInviteImport,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Relationship =
        Relationship(
            relationshipId = offlineHandshakeRelationshipId(
                inviteId = pendingInvite.inviteId,
                inviterFingerprint = pendingInvite.inviterFingerprint,
                responderFingerprint = localIdentity.fingerprint,
            ),
            localIdentityPublicKey = localIdentity.publicKeyEncoded,
            peerPublicKey = pendingInvite.inviterPublicKeyEncoded,
            peerDisplayName = pendingInvite.inviterDisplayName,
            peerFingerprint = pendingInvite.inviterFingerprint,
            realmId = pendingInvite.realmId,
            state = RelationshipState.PENDING_IMPORT,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            sourceInviteId = pendingInvite.inviteId,
            offlineHandshakeRole = OfflineHandshakeRole.RESPONDER,
            cryptoProfileId = pendingInvite.cryptoProfileId,
            cryptoProfileHash = pendingInvite.cryptoProfileHash,
            admissionDecisionHash = pendingInvite.admissionDecisionHash,
            profilePolicyVersion = pendingInvite.profilePolicyVersion,
            nativeBackendVersion = pendingInvite.nativeBackendVersion,
        )

    fun startHandshake(relationship: Relationship, nowEpochMillis: Long = System.currentTimeMillis()): Relationship =
        if (relationship.state == RelationshipState.PENDING_IMPORT) {
            relationship.copy(
                state = RelationshipState.PENDING_HANDSHAKE,
                updatedAtEpochMillis = nowEpochMillis,
            )
        } else {
            relationship
        }

    fun acceptHandshake(relationship: Relationship, nowEpochMillis: Long = System.currentTimeMillis()): Relationship =
        if (relationship.state == RelationshipState.PENDING_HANDSHAKE) {
            relationship.copy(
                state = RelationshipState.ACTIVE,
                updatedAtEpochMillis = nowEpochMillis,
            )
        } else {
            relationship
        }

    fun unlinkRelationship(
        relationship: Relationship,
        reason: UnlinkReason,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): RelationshipUnlinkResult {
        val updated = relationship.copy(
            state = RelationshipState.UNLINKED,
            updatedAtEpochMillis = nowEpochMillis,
        )
        val notice = UnlinkNotice(
            relationshipId = relationship.relationshipId,
            fromPublicKey = relationship.localIdentityPublicKey,
            toPublicKey = relationship.peerPublicKey,
            reason = reason,
            createdAtEpochMillis = nowEpochMillis,
            signature = null,
        )
        val complaint = if (reason.isNegative() && relationship.realmId != null) {
            ComplaintEvent(
                complaintId = "complaint-${UUID.randomUUID()}",
                realmId = relationship.realmId,
                targetPublicKey = relationship.peerPublicKey,
                sourceRelationshipId = relationship.relationshipId,
                reason = reason,
                createdAtEpochMillis = nowEpochMillis,
            )
        } else {
            null
        }

        return RelationshipUnlinkResult(updated, notice, complaint)
    }

    fun applyPeerUnlinkNotice(
        relationship: Relationship,
        notice: UnlinkNotice,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Relationship {
        val appliesToRelationship = notice.relationshipId == relationship.relationshipId
        val sentByPeer = notice.fromPublicKey == relationship.peerPublicKey
        val sentToLocal = notice.toPublicKey == relationship.localIdentityPublicKey
        return if (appliesToRelationship && sentByPeer && sentToLocal) {
            relationship.copy(
                state = RelationshipState.BLOCKED_BY_PEER,
                updatedAtEpochMillis = nowEpochMillis,
            )
        } else {
            relationship
        }
    }

    fun canSendMessage(relationship: Relationship): Boolean =
        relationship.state == RelationshipState.ACTIVE

    fun offlineHandshakeRelationshipId(
        inviteId: String,
        inviterFingerprint: String,
        responderFingerprint: String,
    ): String =
        "relationship-${stableToken(inviteId)}-${stableToken(inviterFingerprint)}-${stableToken(responderFingerprint)}"

    fun requiresRejoin(relationship: Relationship): Boolean =
        relationship.state in setOf(
            RelationshipState.UNLINKED,
            RelationshipState.BLOCKED_BY_PEER,
            RelationshipState.REJOIN_REQUIRED,
        )

    fun UnlinkReason.isNegative(): Boolean =
        this in setOf(
            UnlinkReason.UNWANTED_MESSAGES,
            UnlinkReason.SPAM,
            UnlinkReason.THREAT_PRESSURE_OR_ETHICS_ABUSE,
        )

    private fun stableToken(value: String): String =
        value.filter { it.isLetterOrDigit() }.take(12).ifBlank { "unknown" }
}
