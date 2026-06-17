package com.disser.kraken.realm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalEvaluatorTest {
    @Test
    fun singleAdminApprovalByAdminApprovesAndCreatesCertificate() {
        val request = request(policy = ApprovalPolicy.singleAdmin())

        val outcome = ApprovalEvaluator.recordDecision(
            request = request,
            decision = decision("admin-key", RealmRole.ADMIN, ApprovalDecisionType.APPROVE),
            issuedByPublicKey = "admin-key",
            nowEpochMillis = 100,
        )

        assertEquals(PendingMembershipState.APPROVED, outcome.request.state)
        assertEquals(request.inviteePublicKey, outcome.membershipCertificate?.memberPublicKey)
        assertEquals(request.realmId, outcome.membershipCertificate?.realmId)
    }

    @Test
    fun memberApprovalDoesNotApprove() {
        val request = request(policy = ApprovalPolicy.singleAdmin())

        val outcome = ApprovalEvaluator.recordDecision(
            request = request,
            decision = decision("member-key", RealmRole.MEMBER, ApprovalDecisionType.APPROVE),
            issuedByPublicKey = "admin-key",
        )

        assertEquals(PendingMembershipState.PENDING_REVIEW, outcome.request.state)
        assertNull(outcome.membershipCertificate)
    }

    @Test
    fun thresholdRequiresUniqueEligibleApprovers() {
        val request = request(
            policy = ApprovalPolicy(
                mode = ApprovalMode.THRESHOLD,
                requiredApprovals = 2,
                eligibleRoles = listOf(RealmRole.ADMIN, RealmRole.MODERATOR),
            )
        )

        val first = ApprovalEvaluator.recordDecision(
            request = request,
            decision = decision("admin-key", RealmRole.ADMIN, ApprovalDecisionType.APPROVE),
            issuedByPublicKey = "admin-key",
        )
        val duplicate = ApprovalEvaluator.recordDecision(
            request = first.request,
            decision = decision("admin-key", RealmRole.ADMIN, ApprovalDecisionType.APPROVE),
            issuedByPublicKey = "admin-key",
        )
        val second = ApprovalEvaluator.recordDecision(
            request = duplicate.request,
            decision = decision("moderator-key", RealmRole.MODERATOR, ApprovalDecisionType.APPROVE),
            issuedByPublicKey = "admin-key",
        )

        assertEquals(PendingMembershipState.PENDING_REVIEW, duplicate.request.state)
        assertEquals(1, ApprovalEvaluator.approvalCount(duplicate.request))
        assertEquals(PendingMembershipState.APPROVED, second.request.state)
    }

    @Test
    fun eligibleRejectRejectsRequest() {
        val request = request(policy = ApprovalPolicy.singleAdmin())

        val outcome = ApprovalEvaluator.recordDecision(
            request = request,
            decision = decision("admin-key", RealmRole.ADMIN, ApprovalDecisionType.REJECT),
            issuedByPublicKey = "admin-key",
        )

        assertEquals(PendingMembershipState.REJECTED, outcome.request.state)
        assertNull(outcome.membershipCertificate)
    }

    @Test
    fun pendingUserRightsAreRestricted() {
        val rights = ApprovalEvaluator.pendingUserRights

        assertFalse(rights.canWriteFirst)
        assertFalse(rights.canPost)
        assertFalse(rights.canInviteOthers)
        assertFalse(rights.canReceiveBacklog)
        assertTrue(rights.canReplyToApprovalMessages)
    }

    private fun request(policy: ApprovalPolicy): PendingMembershipRequest =
        PendingMembershipRequest(
            requestId = "request-1",
            realmId = "realm-1",
            inviteId = "invite-1",
            inviterPublicKey = "inviter-key",
            inviteePublicKey = "invitee-key",
            inviteeDisplayName = "Invitee",
            createdAtEpochMillis = 1,
            state = PendingMembershipState.PENDING_REVIEW,
            approvalPolicy = policy,
            decisions = emptyList(),
        )

    private fun decision(
        approverPublicKey: String,
        role: RealmRole,
        type: ApprovalDecisionType,
    ): ApprovalDecision =
        ApprovalDecision(
            approverPublicKey = approverPublicKey,
            approverRole = role,
            decisionType = type,
            decidedAtEpochMillis = 2,
        )
}
