package com.disser.kraken.realm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Realm(
    @SerialName("realm_id")
    val realmId: String,
    val name: String,
    val description: String?,
    @SerialName("created_by_public_key")
    val createdByPublicKey: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    val policy: RealmPolicy,
    @SerialName("capacity_state")
    val capacityState: CapacityState,
    @SerialName("local_state")
    val localState: LocalRealmState,
)

@Serializable
enum class LocalRealmState {
    ACTIVE,
    ONLY_DIRECT,
    PAUSED,
    ARCHIVED,
    LEFT,
}

@Serializable
data class RealmPolicy(
    @SerialName("max_members")
    val maxMembers: Int = 500,
    @SerialName("max_ttl_hours")
    val maxTtlHours: Int = 24,
    @SerialName("max_copy_budget")
    val maxCopyBudget: Int = 8,
    @SerialName("allow_transit")
    val allowTransit: Boolean = true,
    @SerialName("allow_courier_score")
    val allowCourierScore: Boolean = true,
    @SerialName("read_receipts_default")
    val readReceiptsDefault: Boolean = false,
    @SerialName("small_group_max_members")
    val smallGroupMaxMembers: Int = 10,
)

@Serializable
data class CapacityState(
    @SerialName("member_count")
    val memberCount: Int,
    val capacity: Int,
    val epoch: Long,
    @SerialName("signed_statement")
    val signedStatement: String? = null,
    @SerialName("capacity_token")
    val capacityToken: String? = null,
)

@Serializable
data class MembershipCertificate(
    val type: String = "membership_certificate",
    @SerialName("realm_id")
    val realmId: String,
    @SerialName("membership_id")
    val membershipId: String,
    @SerialName("member_public_key")
    val memberPublicKey: String,
    @SerialName("issued_by_public_key")
    val issuedByPublicKey: String,
    @SerialName("issued_at_epoch_millis")
    val issuedAtEpochMillis: Long,
    @SerialName("expires_at_epoch_millis")
    val expiresAtEpochMillis: Long?,
    val capabilities: List<String>,
    val signature: String? = null,
)

@Serializable
data class InviteEdge(
    @SerialName("realm_id")
    val realmId: String,
    @SerialName("invite_id")
    val inviteId: String,
    @SerialName("inviter_public_key")
    val inviterPublicKey: String,
    @SerialName("invitee_public_key")
    val inviteePublicKey: String,
    @SerialName("membership_id")
    val membershipId: String?,
    @SerialName("accepted_at_epoch_millis")
    val acceptedAtEpochMillis: Long,
    @SerialName("inviter_signature")
    val inviterSignature: String? = null,
    @SerialName("invitee_signature")
    val inviteeSignature: String? = null,
)

@Serializable
data class PendingMembershipRequest(
    @SerialName("request_id")
    val requestId: String,
    @SerialName("realm_id")
    val realmId: String,
    @SerialName("invite_id")
    val inviteId: String,
    @SerialName("inviter_public_key")
    val inviterPublicKey: String,
    @SerialName("invitee_public_key")
    val inviteePublicKey: String,
    @SerialName("invitee_display_name")
    val inviteeDisplayName: String?,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    val state: PendingMembershipState,
    @SerialName("approval_policy")
    val approvalPolicy: ApprovalPolicy = ApprovalPolicy.singleAdmin(),
    val decisions: List<ApprovalDecision> = emptyList(),
)

@Serializable
enum class PendingMembershipState {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED,
}

@Serializable
data class ApprovalPolicy(
    val mode: ApprovalMode,
    @SerialName("required_approvals")
    val requiredApprovals: Int,
    @SerialName("eligible_roles")
    val eligibleRoles: List<RealmRole>,
) {
    companion object {
        fun singleAdmin(): ApprovalPolicy =
            ApprovalPolicy(
                mode = ApprovalMode.SINGLE_ADMIN,
                requiredApprovals = 1,
                eligibleRoles = listOf(RealmRole.OWNER, RealmRole.ADMIN),
            )
    }
}

@Serializable
enum class ApprovalMode {
    SINGLE_ADMIN,
    THRESHOLD,
}

@Serializable
enum class RealmRole {
    OWNER,
    ADMIN,
    MODERATOR,
    MEMBER,
}

@Serializable
data class ApprovalDecision(
    @SerialName("approver_public_key")
    val approverPublicKey: String,
    @SerialName("approver_role")
    val approverRole: RealmRole,
    @SerialName("decision_type")
    val decisionType: ApprovalDecisionType,
    @SerialName("decided_at_epoch_millis")
    val decidedAtEpochMillis: Long,
)

@Serializable
enum class ApprovalDecisionType {
    APPROVE,
    REJECT,
}

data class PendingUserRights(
    val canWriteFirst: Boolean,
    val canPost: Boolean,
    val canInviteOthers: Boolean,
    val canReceiveBacklog: Boolean,
    val canReplyToApprovalMessages: Boolean,
)

data class ApprovalOutcome(
    val request: PendingMembershipRequest,
    val membershipCertificate: MembershipCertificate?,
)

data class DemoRealmCreation(
    val realm: Realm,
    val membershipCertificate: MembershipCertificate,
)
