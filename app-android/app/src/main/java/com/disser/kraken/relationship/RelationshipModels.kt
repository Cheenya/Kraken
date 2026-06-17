package com.disser.kraken.relationship

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RelationshipState {
    PENDING_IMPORT,
    PENDING_HANDSHAKE,
    ACTIVE,
    UNLINK_REQUESTED,
    UNLINKED,
    BLOCKED_BY_PEER,
    REJOIN_REQUIRED,
}

@Serializable
enum class OfflineHandshakeRole {
    INVITER,
    RESPONDER,
}

@Serializable
enum class UnlinkReason {
    ENDED_INTERACTION,
    UNWANTED_MESSAGES,
    SPAM,
    THREAT_PRESSURE_OR_ETHICS_ABUSE,
    OTHER,
}

@Serializable
data class Relationship(
    @SerialName("relationship_id")
    val relationshipId: String,
    @SerialName("local_identity_public_key")
    val localIdentityPublicKey: String,
    @SerialName("peer_public_key")
    val peerPublicKey: String,
    @SerialName("peer_display_name")
    val peerDisplayName: String?,
    @SerialName("peer_fingerprint")
    val peerFingerprint: String,
    @SerialName("realm_id")
    val realmId: String?,
    val state: RelationshipState,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @SerialName("source_invite_id")
    val sourceInviteId: String?,
    @SerialName("offline_handshake_role")
    val offlineHandshakeRole: OfflineHandshakeRole? = null,
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
)

@Serializable
data class UnlinkNotice(
    val type: String = "unlink_notice",
    val version: Int = 1,
    @SerialName("relationship_id")
    val relationshipId: String,
    @SerialName("from_public_key")
    val fromPublicKey: String,
    @SerialName("to_public_key")
    val toPublicKey: String,
    val reason: UnlinkReason,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    val signature: String? = null,
)

@Serializable
data class ComplaintEvent(
    @SerialName("complaint_id")
    val complaintId: String,
    @SerialName("realm_id")
    val realmId: String,
    @SerialName("target_public_key")
    val targetPublicKey: String,
    @SerialName("source_relationship_id")
    val sourceRelationshipId: String,
    val reason: UnlinkReason,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

data class RelationshipUnlinkResult(
    val relationship: Relationship,
    val unlinkNotice: UnlinkNotice,
    val complaintEvent: ComplaintEvent?,
)
