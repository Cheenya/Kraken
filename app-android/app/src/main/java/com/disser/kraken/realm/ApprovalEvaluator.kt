package com.disser.kraken.realm

import java.util.UUID

object ApprovalEvaluator {
    val pendingUserRights = PendingUserRights(
        canWriteFirst = false,
        canPost = false,
        canInviteOthers = false,
        canReceiveBacklog = false,
        canReplyToApprovalMessages = true,
    )

    fun recordDecision(
        request: PendingMembershipRequest,
        decision: ApprovalDecision,
        issuedByPublicKey: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ApprovalOutcome {
        if (request.state != PendingMembershipState.PENDING_REVIEW) {
            return ApprovalOutcome(request, membershipCertificate = null)
        }

        val updatedDecisions = request.decisions
            .filterNot { it.approverPublicKey == decision.approverPublicKey }
            .plus(decision)
        val evaluatedState = evaluateState(request.approvalPolicy, updatedDecisions)
        val updatedRequest = request.copy(state = evaluatedState, decisions = updatedDecisions)
        val certificate = if (evaluatedState == PendingMembershipState.APPROVED) {
            createMembershipCertificate(updatedRequest, issuedByPublicKey, nowEpochMillis)
        } else {
            null
        }
        return ApprovalOutcome(updatedRequest, certificate)
    }

    fun evaluateState(
        policy: ApprovalPolicy,
        decisions: List<ApprovalDecision>,
    ): PendingMembershipState {
        if (decisions.any { it.decisionType == ApprovalDecisionType.REJECT && it.approverRole in policy.eligibleRoles }) {
            return PendingMembershipState.REJECTED
        }

        val uniqueEligibleApprovals = decisions
            .filter { it.decisionType == ApprovalDecisionType.APPROVE && it.approverRole in policy.eligibleRoles }
            .distinctBy { it.approverPublicKey }

        return when (policy.mode) {
            ApprovalMode.SINGLE_ADMIN -> {
                val hasOwnerOrAdminApproval = uniqueEligibleApprovals.any {
                    it.approverRole == RealmRole.OWNER || it.approverRole == RealmRole.ADMIN
                }
                if (hasOwnerOrAdminApproval) PendingMembershipState.APPROVED else PendingMembershipState.PENDING_REVIEW
            }
            ApprovalMode.THRESHOLD -> {
                if (uniqueEligibleApprovals.size >= policy.requiredApprovals) {
                    PendingMembershipState.APPROVED
                } else {
                    PendingMembershipState.PENDING_REVIEW
                }
            }
        }
    }

    fun approvalCount(request: PendingMembershipRequest): Int =
        request.decisions
            .filter {
                it.decisionType == ApprovalDecisionType.APPROVE &&
                    it.approverRole in request.approvalPolicy.eligibleRoles
            }
            .distinctBy { it.approverPublicKey }
            .size

    private fun createMembershipCertificate(
        request: PendingMembershipRequest,
        issuedByPublicKey: String,
        nowEpochMillis: Long,
    ): MembershipCertificate =
        MembershipCertificate(
            realmId = request.realmId,
            membershipId = "membership-${UUID.randomUUID()}",
            memberPublicKey = request.inviteePublicKey,
            issuedByPublicKey = issuedByPublicKey,
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = null,
            capabilities = listOf("send_direct", "relay_basic"),
            signature = null,
        )
}
