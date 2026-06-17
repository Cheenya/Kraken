package com.disser.kraken.invite

import com.disser.kraken.identity.LocalIdentity
import java.util.UUID

class InviteImportService {
    fun import(
        rawJson: String,
        localIdentity: LocalIdentity?,
        existingImports: List<PendingInviteImport>,
    ): InviteImportResult {
        val payload = InvitePayloadCodec.decode(rawJson).getOrElse { error ->
            return InviteImportResult.Error(error.message ?: "Invalid invite JSON.")
        }

        validate(payload, localIdentity, existingImports)?.let { error ->
            return InviteImportResult.Error(error)
        }

        val pendingImport = PendingInviteImport(
            localId = "pending-${UUID.randomUUID()}",
            inviteId = payload.inviteId,
            scope = payload.scope,
            realmId = payload.realmId,
            realmName = payload.realmName,
            inviterDisplayName = payload.inviterDisplayName,
            inviterPublicKeyEncoded = payload.inviterPublicKeyEncoded,
            inviterFingerprint = payload.inviterFingerprint,
            expiresAtEpochMillis = payload.expiresAtEpochMillis,
            oneTime = payload.oneTime,
            requiresHandshake = payload.requiresHandshake,
            requiresApproval = payload.requiresApproval,
            cryptoProfileId = payload.cryptoProfileId,
            cryptoProfileHash = payload.cryptoProfileHash,
            admissionDecisionHash = payload.admissionDecisionHash,
            profilePolicyVersion = payload.profilePolicyVersion,
            nativeBackendVersion = payload.nativeBackendVersion,
            importedAtEpochMillis = System.currentTimeMillis(),
            state = PendingInviteState.PENDING_IMPORT,
        )

        return InviteImportResult.Success(payload, pendingImport)
    }

    private fun validate(
        payload: OneTimeInvitePayload,
        localIdentity: LocalIdentity?,
        existingImports: List<PendingInviteImport>,
    ): String? {
        return when {
            payload.type != OneTimeInvitePayload.TYPE -> "Unsupported invite type."
            payload.version != OneTimeInvitePayload.VERSION -> "Unsupported invite version."
            payload.inviteId.isBlank() -> "Missing invite_id."
            payload.scope == InviteScope.REALM_MEMBERSHIP && payload.realmId.isNullOrBlank() ->
                "Missing realm_id."
            payload.inviterPublicKeyEncoded.isBlank() -> "Missing inviter public key."
            payload.inviterFingerprint.isBlank() -> "Missing inviter fingerprint."
            localIdentity != null && payload.inviterPublicKeyEncoded == localIdentity.publicKeyEncoded ->
                "Self-invite is not allowed."
            existingImports.any { it.inviteId == payload.inviteId } ->
                "This invite was already imported."
            existingImports.any { it.inviterPublicKeyEncoded == payload.inviterPublicKeyEncoded } ->
                "Ключ уже знаком"
            else -> null
        }
    }
}
