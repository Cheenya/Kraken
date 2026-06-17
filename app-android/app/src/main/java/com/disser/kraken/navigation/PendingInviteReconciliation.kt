package com.disser.kraken.navigation

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.OneTimeInvitePayload
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState

internal fun reconcilePendingInvitesForFreshInviteScan(
    existingImports: List<PendingInviteImport>,
    relationships: List<Relationship>,
    localIdentity: LocalIdentity?,
    payload: OneTimeInvitePayload,
): List<PendingInviteImport> {
    val identity = localIdentity ?: return existingImports
    if (payload.inviterPublicKeyEncoded == identity.publicKeyEncoded) return existingImports
    if (relationships.hasKnownRelationshipForInvite(identity, payload)) return existingImports

    return existingImports.filterNot { pending ->
        pending.inviteId == payload.inviteId ||
            pending.inviterPublicKeyEncoded == payload.inviterPublicKeyEncoded ||
            pending.inviterFingerprint == payload.inviterFingerprint
    }
}

internal fun List<Relationship>.knownRelationshipForInvite(
    localIdentity: LocalIdentity,
    payload: OneTimeInvitePayload,
): Relationship? =
    firstOrNull { relationship ->
        relationship.localIdentityPublicKey == localIdentity.publicKeyEncoded &&
            (
                relationship.peerPublicKey == payload.inviterPublicKeyEncoded ||
                    relationship.peerFingerprint == payload.inviterFingerprint
                ) &&
            relationship.state != RelationshipState.UNLINKED &&
            relationship.state != RelationshipState.BLOCKED_BY_PEER
    }

private fun List<Relationship>.hasKnownRelationshipForInvite(
    localIdentity: LocalIdentity,
    payload: OneTimeInvitePayload,
): Boolean =
    knownRelationshipForInvite(localIdentity, payload) != null
