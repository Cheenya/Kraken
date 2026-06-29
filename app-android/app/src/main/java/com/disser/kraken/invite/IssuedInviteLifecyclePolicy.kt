package com.disser.kraken.invite

import com.disser.kraken.handshake.HandshakeResponsePayload
import com.disser.kraken.handshake.KnownInviteLifecycle

object IssuedInviteLifecyclePolicy {
    fun knownLifecycleForResponse(
        payload: HandshakeResponsePayload,
        issuedInvites: List<IssuedInviteRecord>,
        localFingerprint: String,
    ): Result<KnownInviteLifecycle> =
        runCatching {
            val issuedInvite = issuedInvites.firstOrNull { it.inviteId == payload.inviteId }
                ?: throw IllegalArgumentException("Это ответ на неизвестное локальное приглашение.")
            if (payload.realmId == null && !payload.requiresApproval) {
                if (
                    issuedInvite.scope != InviteScope.DIRECT_CONTACT ||
                    issuedInvite.realmId != null ||
                    issuedInvite.inviterFingerprint != localFingerprint
                ) {
                    throw IllegalArgumentException("Этот ответ относится к другому типу приглашения.")
                }
            } else if (issuedInvite.scope != InviteScope.REALM_MEMBERSHIP || issuedInvite.realmId != payload.realmId) {
                throw IllegalArgumentException("Этот ответ относится к другому типу приглашения или другому реалму.")
            }
            issuedInvite.toKnownInviteLifecycle()
        }
}
