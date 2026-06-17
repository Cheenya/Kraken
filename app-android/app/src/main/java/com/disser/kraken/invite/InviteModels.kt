package com.disser.kraken.invite

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OneTimeInvitePayload(
    val type: String = TYPE,
    val version: Int = VERSION,
    @SerialName("invite_id")
    val inviteId: String,
    val scope: InviteScope = InviteScope.DIRECT_CONTACT,
    @SerialName("realm_id")
    val realmId: String? = null,
    @SerialName("realm_name")
    val realmName: String? = null,
    @SerialName("inviter_display_name")
    val inviterDisplayName: String,
    @SerialName("inviter_public_key_encoded")
    val inviterPublicKeyEncoded: String,
    @SerialName("inviter_fingerprint")
    val inviterFingerprint: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("expires_at_epoch_millis")
    val expiresAtEpochMillis: Long?,
    @SerialName("one_time")
    val oneTime: Boolean = true,
    @SerialName("requires_handshake")
    val requiresHandshake: Boolean = true,
    @SerialName("requires_approval")
    val requiresApproval: Boolean = false,
    val nonce: String = "",
    val capabilities: List<String>,
    @SerialName("crypto_profile_id")
    val cryptoProfileId: String? = null,
    @SerialName("crypto_profile_hash")
    val cryptoProfileHash: String? = null,
    @SerialName("admission_decision_hash")
    val admissionDecisionHash: String? = null,
    @SerialName("profile_policy_version")
    val profilePolicyVersion: Int? = null,
    @SerialName("native_backend_version")
    val nativeBackendVersion: String? = null,
    val signature: String? = null,
) {
    companion object {
        const val TYPE = "one_time_invite"
        const val VERSION = 1
    }
}

@Serializable
enum class InviteScope {
    DIRECT_CONTACT,
    REALM_MEMBERSHIP,
}

@Serializable
data class PendingInviteImport(
    @SerialName("local_id")
    val localId: String,
    @SerialName("invite_id")
    val inviteId: String,
    val scope: InviteScope = InviteScope.DIRECT_CONTACT,
    @SerialName("realm_id")
    val realmId: String? = null,
    @SerialName("realm_name")
    val realmName: String? = null,
    @SerialName("inviter_display_name")
    val inviterDisplayName: String,
    @SerialName("inviter_public_key_encoded")
    val inviterPublicKeyEncoded: String,
    @SerialName("inviter_fingerprint")
    val inviterFingerprint: String,
    @SerialName("expires_at_epoch_millis")
    val expiresAtEpochMillis: Long? = null,
    @SerialName("one_time")
    val oneTime: Boolean = true,
    @SerialName("requires_handshake")
    val requiresHandshake: Boolean = true,
    @SerialName("requires_approval")
    val requiresApproval: Boolean = false,
    @SerialName("crypto_profile_id")
    val cryptoProfileId: String? = null,
    @SerialName("crypto_profile_hash")
    val cryptoProfileHash: String? = null,
    @SerialName("admission_decision_hash")
    val admissionDecisionHash: String? = null,
    @SerialName("profile_policy_version")
    val profilePolicyVersion: Int? = null,
    @SerialName("native_backend_version")
    val nativeBackendVersion: String? = null,
    @SerialName("imported_at_epoch_millis")
    val importedAtEpochMillis: Long,
    val state: PendingInviteState,
)

@Serializable
data class IssuedInviteRecord(
    @SerialName("invite_id")
    val inviteId: String,
    val scope: InviteScope = InviteScope.DIRECT_CONTACT,
    @SerialName("realm_id")
    val realmId: String? = null,
    @SerialName("realm_name")
    val realmName: String? = null,
    @SerialName("inviter_fingerprint")
    val inviterFingerprint: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("expires_at_epoch_millis")
    val expiresAtEpochMillis: Long? = null,
    val revoked: Boolean = false,
    val consumed: Boolean = false,
    @SerialName("consumed_at_epoch_millis")
    val consumedAtEpochMillis: Long? = null,
    @SerialName("consumed_by_public_key")
    val consumedByPublicKey: String? = null,
    val payload: OneTimeInvitePayload? = null,
) {
    companion object {
        fun fromPayload(payload: OneTimeInvitePayload): IssuedInviteRecord =
            IssuedInviteRecord(
                inviteId = payload.inviteId,
                scope = payload.scope,
                realmId = payload.realmId,
                realmName = payload.realmName,
                inviterFingerprint = payload.inviterFingerprint,
                createdAtEpochMillis = payload.createdAtEpochMillis,
                expiresAtEpochMillis = payload.expiresAtEpochMillis,
                payload = payload,
            )
    }
}

@Serializable
enum class PendingInviteState {
    PENDING_IMPORT,
    PENDING_HANDSHAKE,
}

sealed class InviteImportResult {
    data class Success(
        val payload: OneTimeInvitePayload,
        val pendingImport: PendingInviteImport,
    ) : InviteImportResult()

    data class Error(val reason: String) : InviteImportResult()
}
