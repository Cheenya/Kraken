package com.disser.kraken.handshake

import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState

data class KnownInviteLifecycle(
    val inviteId: String,
    val revoked: Boolean = false,
    val consumed: Boolean = false,
    val expiresAtEpochMillis: Long? = null,
)

sealed class OfflineHandshakeResult {
    data class ResponseAccepted(
        val relationship: Relationship,
        val confirmationPayload: HandshakeConfirmationPayload,
        val idempotent: Boolean,
    ) : OfflineHandshakeResult()

    data class ConfirmationAccepted(
        val relationship: Relationship,
        val idempotent: Boolean,
    ) : OfflineHandshakeResult()

    data class Error(val reason: String) : OfflineHandshakeResult()
}

class OfflineHandshakeService(
    private val admissionGate: ProductCryptoAdmissionGate = ProductCryptoAdmissionGate(),
) {
    fun generateResponsePayload(
        localIdentity: LocalIdentity,
        relationship: Relationship,
        nowEpochMillis: Long = stableCreatedAt(relationship),
    ): Result<HandshakeResponsePayload> = runCatching {
        require(relationship.state in setOf(RelationshipState.PENDING_IMPORT, RelationshipState.PENDING_HANDSHAKE)) {
            "Response QR is available only for pending relationships."
        }
        val inviteId = requireNotNull(relationship.sourceInviteId) {
            "Pending relationship has no source invite."
        }
        require(relationship.peerFingerprint.isNotBlank()) { "Inviter fingerprint is missing." }
        HandshakeResponsePayload(
            responseId = responseIdFor(inviteId, localIdentity.fingerprint),
            inviteId = inviteId,
            realmId = relationship.realmId,
            requiresApproval = relationship.realmId != null,
            responderFingerprint = localIdentity.fingerprint,
            responderDisplayName = localIdentity.displayName,
            responderPublicKeyEncoded = localIdentity.publicKeyEncoded,
            inviterFingerprint = relationship.peerFingerprint,
            createdAtEpochMillis = nowEpochMillis,
            relationshipHint = relationship.relationshipId,
            cryptoProfileId = relationship.cryptoProfileId,
            cryptoProfileHash = relationship.cryptoProfileHash,
            admissionDecisionHash = relationship.admissionDecisionHash,
            profilePolicyVersion = relationship.profilePolicyVersion,
            nativeBackendVersion = relationship.nativeBackendVersion,
        )
    }

    fun generateConfirmationPayload(
        localIdentity: LocalIdentity,
        relationship: Relationship,
        realmName: String? = null,
        membershipCertificate: MembershipCertificate? = null,
        nowEpochMillis: Long = stableCreatedAt(relationship),
    ): Result<HandshakeConfirmationPayload> = runCatching {
        require(relationship.state == RelationshipState.ACTIVE) {
            "Final confirmation QR is available after accepting a response."
        }
        require(relationship.offlineHandshakeRole == OfflineHandshakeRole.INVITER) {
            "Only the original invite owner can show final confirmation."
        }
        val inviteId = requireNotNull(relationship.sourceInviteId) {
            "Relationship has no source invite."
        }
        HandshakeConfirmationPayload(
            confirmationId = confirmationIdFor(inviteId, relationship.peerFingerprint),
            responseId = responseIdFor(inviteId, relationship.peerFingerprint),
            inviteId = inviteId,
            realmId = relationship.realmId,
            realmName = realmName,
            membershipCertificate = membershipCertificate,
            inviterFingerprint = localIdentity.fingerprint,
            responderFingerprint = relationship.peerFingerprint,
            createdAtEpochMillis = nowEpochMillis,
            cryptoProfileId = relationship.cryptoProfileId,
            cryptoProfileHash = relationship.cryptoProfileHash,
            admissionDecisionHash = relationship.admissionDecisionHash,
            profilePolicyVersion = relationship.profilePolicyVersion,
            nativeBackendVersion = relationship.nativeBackendVersion,
        )
    }

    fun generateConfirmationPayload(
        localIdentity: LocalIdentity,
        responsePayload: HandshakeResponsePayload,
        nowEpochMillis: Long = responsePayload.createdAtEpochMillis,
    ): HandshakeConfirmationPayload =
        HandshakeConfirmationPayload(
            confirmationId = confirmationIdFor(responsePayload.inviteId, responsePayload.responderFingerprint),
            responseId = responsePayload.responseId,
            inviteId = responsePayload.inviteId,
            realmId = responsePayload.realmId,
            inviterFingerprint = localIdentity.fingerprint,
            responderFingerprint = responsePayload.responderFingerprint,
            createdAtEpochMillis = nowEpochMillis,
            cryptoProfileId = responsePayload.cryptoProfileId,
            cryptoProfileHash = responsePayload.cryptoProfileHash,
            admissionDecisionHash = responsePayload.admissionDecisionHash,
            profilePolicyVersion = responsePayload.profilePolicyVersion,
            nativeBackendVersion = responsePayload.nativeBackendVersion,
        )

    fun processResponsePayload(
        localIdentity: LocalIdentity,
        relationships: List<Relationship>,
        payload: HandshakeResponsePayload,
        knownInviteLifecycle: KnownInviteLifecycle? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): OfflineHandshakeResult {
        validateResponse(localIdentity, payload, knownInviteLifecycle, nowEpochMillis)?.let { error ->
            return OfflineHandshakeResult.Error(error)
        }
        validateCryptoProfileBinding(
            cryptoProfileId = payload.cryptoProfileId,
            cryptoProfileHash = payload.cryptoProfileHash,
            admissionDecisionHash = payload.admissionDecisionHash,
            profilePolicyVersion = payload.profilePolicyVersion,
        )?.let { error ->
            return OfflineHandshakeResult.Error(error)
        }

        val existing = relationships.firstOrNull {
            it.peerFingerprint == payload.responderFingerprint ||
                it.peerPublicKey == payload.responderPublicKeyEncoded
        }
        val confirmation = generateConfirmationPayload(localIdentity, payload, nowEpochMillis)
        val sharedRelationshipId = RelationshipService.offlineHandshakeRelationshipId(
            inviteId = payload.inviteId,
            inviterFingerprint = localIdentity.fingerprint,
            responderFingerprint = payload.responderFingerprint,
        )

        if (existing != null) {
            if (existing.sourceInviteId != null && existing.sourceInviteId != payload.inviteId) {
                return OfflineHandshakeResult.Error("This responder is already linked to another invite.")
            }
            val alreadyActive = existing.state == RelationshipState.ACTIVE
            val updated = existing.copy(
                relationshipId = sharedRelationshipId,
                state = RelationshipState.ACTIVE,
                sourceInviteId = existing.sourceInviteId ?: payload.inviteId,
                realmId = existing.realmId ?: payload.realmId,
                peerDisplayName = payload.responderDisplayName,
                peerFingerprint = payload.responderFingerprint,
                offlineHandshakeRole = OfflineHandshakeRole.INVITER,
                cryptoProfileId = payload.cryptoProfileId ?: existing.cryptoProfileId,
                cryptoProfileHash = payload.cryptoProfileHash ?: existing.cryptoProfileHash,
                admissionDecisionHash = payload.admissionDecisionHash ?: existing.admissionDecisionHash,
                profilePolicyVersion = payload.profilePolicyVersion ?: existing.profilePolicyVersion,
                nativeBackendVersion = payload.nativeBackendVersion ?: existing.nativeBackendVersion,
                updatedAtEpochMillis = nowEpochMillis,
            )
            return OfflineHandshakeResult.ResponseAccepted(updated, confirmation, idempotent = alreadyActive)
        }

        val relationship = Relationship(
            relationshipId = sharedRelationshipId,
            localIdentityPublicKey = localIdentity.publicKeyEncoded,
            peerPublicKey = payload.responderPublicKeyEncoded,
            peerDisplayName = payload.responderDisplayName,
            peerFingerprint = payload.responderFingerprint,
            realmId = payload.realmId,
            state = RelationshipState.ACTIVE,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            sourceInviteId = payload.inviteId,
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
            cryptoProfileId = payload.cryptoProfileId,
            cryptoProfileHash = payload.cryptoProfileHash,
            admissionDecisionHash = payload.admissionDecisionHash,
            profilePolicyVersion = payload.profilePolicyVersion,
            nativeBackendVersion = payload.nativeBackendVersion,
        )
        return OfflineHandshakeResult.ResponseAccepted(relationship, confirmation, idempotent = false)
    }

    fun processConfirmationPayload(
        localIdentity: LocalIdentity,
        relationships: List<Relationship>,
        payload: HandshakeConfirmationPayload,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): OfflineHandshakeResult {
        validateConfirmation(localIdentity, payload)?.let { error ->
            return OfflineHandshakeResult.Error(error)
        }
        validateCryptoProfileBinding(
            cryptoProfileId = payload.cryptoProfileId,
            cryptoProfileHash = payload.cryptoProfileHash,
            admissionDecisionHash = payload.admissionDecisionHash,
            profilePolicyVersion = payload.profilePolicyVersion,
        )?.let { error ->
            return OfflineHandshakeResult.Error(error)
        }

        val relationship = relationships.firstOrNull {
            it.peerFingerprint == payload.inviterFingerprint &&
                it.sourceInviteId == payload.inviteId
        } ?: return OfflineHandshakeResult.Error("No pending relationship matches this confirmation.")
        validateConfirmationMembership(localIdentity, relationship, payload, nowEpochMillis)?.let { error ->
            return OfflineHandshakeResult.Error(error)
        }
        validateRelationshipProfileBinding(relationship, payload)?.let { error ->
            return OfflineHandshakeResult.Error(error)
        }

        val alreadyActive = relationship.state == RelationshipState.ACTIVE
        val sharedRelationshipId = RelationshipService.offlineHandshakeRelationshipId(
            inviteId = payload.inviteId,
            inviterFingerprint = payload.inviterFingerprint,
            responderFingerprint = localIdentity.fingerprint,
        )
        val updated = relationship.copy(
            relationshipId = sharedRelationshipId,
            state = RelationshipState.ACTIVE,
            realmId = relationship.realmId ?: payload.realmId,
            offlineHandshakeRole = OfflineHandshakeRole.RESPONDER,
            cryptoProfileId = payload.cryptoProfileId ?: relationship.cryptoProfileId,
            cryptoProfileHash = payload.cryptoProfileHash ?: relationship.cryptoProfileHash,
            admissionDecisionHash = payload.admissionDecisionHash ?: relationship.admissionDecisionHash,
            profilePolicyVersion = payload.profilePolicyVersion ?: relationship.profilePolicyVersion,
            nativeBackendVersion = payload.nativeBackendVersion ?: relationship.nativeBackendVersion,
            updatedAtEpochMillis = nowEpochMillis,
        )
        return OfflineHandshakeResult.ConfirmationAccepted(updated, idempotent = alreadyActive)
    }

    private fun validateResponse(
        localIdentity: LocalIdentity,
        payload: HandshakeResponsePayload,
        knownInviteLifecycle: KnownInviteLifecycle?,
        nowEpochMillis: Long,
    ): String? =
        when {
            payload.type != HandshakeResponsePayload.TYPE -> "Неподдерживаемый тип ответа."
            payload.version != HandshakeResponsePayload.VERSION -> "Неподдерживаемая версия ответа."
            payload.responseId.isBlank() -> "В ответе отсутствует идентификатор."
            payload.inviteId.isBlank() -> "В ответе отсутствует приглашение."
            payload.responderFingerprint.isBlank() -> "В ответе отсутствует отпечаток."
            payload.responderPublicKeyEncoded.isBlank() -> "В ответе отсутствует ключ."
            payload.inviterFingerprint != localIdentity.fingerprint -> "Ответ адресован другому профилю."
            payload.responderFingerprint == localIdentity.fingerprint ||
                payload.responderPublicKeyEncoded == localIdentity.publicKeyEncoded ->
                "Нельзя выполнить сопряжение с собственным профилем."
            knownInviteLifecycle != null && knownInviteLifecycle.inviteId != payload.inviteId ->
                "Ответ ссылается на неизвестное приглашение."
            knownInviteLifecycle?.revoked == true -> "Это приглашение уже отозвано."
            knownInviteLifecycle?.consumed == true -> "Это приглашение уже использовано."
            knownInviteLifecycle?.expiresAtEpochMillis != null && nowEpochMillis >= knownInviteLifecycle.expiresAtEpochMillis ->
                "Срок действия приглашения истёк."
            else -> null
        }

    private fun validateConfirmationMembership(
        localIdentity: LocalIdentity,
        relationship: Relationship,
        payload: HandshakeConfirmationPayload,
        nowEpochMillis: Long,
    ): String? {
        val certificate = payload.membershipCertificate ?: return null
        val realmId = payload.realmId ?: return "В подтверждении членства отсутствует реалм."
        return when {
            relationship.realmId != null && relationship.realmId != realmId ->
                "Подтверждение членства относится к другому реалму."
            certificate.realmId != realmId ->
                "Реалм сертификата членства не совпадает с подтверждением."
            certificate.memberPublicKey != localIdentity.publicKeyEncoded ->
                "Сертификат членства адресован другому профилю."
            certificate.issuedByPublicKey != relationship.peerPublicKey ->
                "Автор сертификата членства не совпадает с пригласившим контактом."
            certificate.expiresAtEpochMillis != null && nowEpochMillis >= certificate.expiresAtEpochMillis ->
                "Срок действия сертификата членства истёк."
            certificate.capabilities.isEmpty() ->
                "В сертификате членства отсутствуют права."
            else -> null
        }
    }

    private fun validateConfirmation(
        localIdentity: LocalIdentity,
        payload: HandshakeConfirmationPayload,
    ): String? =
        when {
            payload.type != HandshakeConfirmationPayload.TYPE -> "Неподдерживаемый тип подтверждения."
            payload.version != HandshakeConfirmationPayload.VERSION -> "Неподдерживаемая версия подтверждения."
            payload.confirmationId.isBlank() -> "В подтверждении отсутствует идентификатор."
            payload.responseId.isBlank() -> "В подтверждении отсутствует ответ."
            payload.inviteId.isBlank() -> "В подтверждении отсутствует приглашение."
            payload.responderFingerprint != localIdentity.fingerprint -> "Подтверждение адресовано другому профилю."
            payload.inviterFingerprint == localIdentity.fingerprint -> "Нельзя подтвердить сопряжение с собственным профилем."
            else -> null
        }

    private fun validateCryptoProfileBinding(
        cryptoProfileId: String?,
        cryptoProfileHash: String?,
        admissionDecisionHash: String?,
        profilePolicyVersion: Int?,
    ): String? {
        val effectiveProfileId = cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val result = admissionGate.evaluate(effectiveProfileId)
            ?: return "Криптографический профиль неизвестен и не может активировать контакт."
        if (cryptoProfileHash != null && cryptoProfileHash != result.profileHash) {
            return "Криптографический профиль не совпадает с локальной политикой допуска."
        }
        if (admissionDecisionHash != null && admissionDecisionHash != result.decisionHash) {
            return "Решение допуска криптографического профиля не совпадает с локальной проверкой."
        }
        if (profilePolicyVersion != null && profilePolicyVersion != result.policyVersion) {
            return "Версия политики допуска криптографического профиля не совпадает."
        }
        return if (result.acceptedForPacketPolicy) {
            null
        } else {
            "Криптографический профиль отклонён Adamova admission gate: ${result.decision}."
        }
    }

    private fun validateRelationshipProfileBinding(
        relationship: Relationship,
        payload: HandshakeConfirmationPayload,
    ): String? {
        val relationshipProfileId = relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val payloadProfileId = payload.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        return when {
            relationshipProfileId != payloadProfileId ->
                "Финальный QR подтверждает другой криптографический профиль."
            relationship.cryptoProfileHash != null &&
                payload.cryptoProfileHash != null &&
                relationship.cryptoProfileHash != payload.cryptoProfileHash ->
                "Hash криптографического профиля в финальном QR не совпадает с pending relationship."
            relationship.admissionDecisionHash != null &&
                payload.admissionDecisionHash != null &&
                relationship.admissionDecisionHash != payload.admissionDecisionHash ->
                "Решение допуска криптографического профиля в финальном QR не совпадает с pending relationship."
            else -> null
        }
    }

    companion object {
        fun responseIdFor(inviteId: String, responderFingerprint: String): String =
            "response-${stableToken(inviteId)}-${stableToken(responderFingerprint)}"

        fun confirmationIdFor(inviteId: String, responderFingerprint: String): String =
            "confirmation-${stableToken(inviteId)}-${stableToken(responderFingerprint)}"

        private fun stableCreatedAt(relationship: Relationship): Long =
            relationship.updatedAtEpochMillis.takeIf { it > 0 } ?: relationship.createdAtEpochMillis

        private fun stableToken(value: String): String =
            value.filter { it.isLetterOrDigit() }.take(12).ifBlank { "unknown" }
    }
}
