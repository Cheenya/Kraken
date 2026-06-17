package com.disser.kraken.relationship

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplaintModeratorTest {
    @Test
    fun negativeUnlinkMapsToComplaintCategory() {
        assertEquals(
            ComplaintCategory.SPAM,
            with(ComplaintModerator) { UnlinkReason.SPAM.toComplaintCategory() },
        )
        assertEquals(
            ComplaintCategory.THREAT_PRESSURE_OR_ETHICS_ABUSE,
            with(ComplaintModerator) { UnlinkReason.THREAT_PRESSURE_OR_ETHICS_ABUSE.toComplaintCategory() },
        )
    }

    @Test
    fun complaintAggregationGroupsByRealmTargetAndReason() {
        val aggregates = ComplaintModerator.aggregate(
            listOf(
                event("realm-1", "target-key", "relationship-1", UnlinkReason.SPAM),
                event("realm-1", "target-key", "relationship-2", UnlinkReason.SPAM),
                event("realm-1", "other-key", "relationship-3", UnlinkReason.UNWANTED_MESSAGES),
            )
        )

        assertEquals(2, aggregates.size)
        assertEquals(2, aggregates.first().complaintCount)
        assertEquals(setOf("relationship-1", "relationship-2"), aggregates.first().sourceRelationshipIds)
    }

    @Test
    fun localComplaintDoesNotEscalateToRootByDefault() {
        val aggregate = ComplaintModerator.aggregate(
            listOf(event("realm-1", "target-key", "relationship-1", UnlinkReason.SPAM))
        ).single()

        assertNull(ComplaintModerator.governanceEscalation(aggregate))
    }

    @Test
    fun governanceEscalationOnlyForGovernanceAbuse() {
        val aggregate = ComplaintAggregate(
            realmId = "realm-1",
            targetPublicKey = "target-key",
            category = ComplaintCategory.GOVERNANCE_ABUSE,
            complaintCount = 3,
            sourceRelationshipIds = setOf("relationship-1"),
        )

        assertEquals(ComplaintCategory.GOVERNANCE_ABUSE, ComplaintModerator.governanceEscalation(aggregate)?.category)
    }

    @Test
    fun singleComplaintDoesNotAutoBan() {
        val aggregate = ComplaintModerator.aggregate(
            listOf(event("realm-1", "target-key", "relationship-1", UnlinkReason.SPAM))
        ).single()

        assertEquals(ModerationAction.WATCH, ComplaintModerator.recommendedAction(aggregate))
        assertNull(ComplaintModerator.createRestriction(aggregate))
    }

    @Test
    fun repeatedSpamCanRestrictInvitesWithoutAutoBan() {
        val aggregate = ComplaintModerator.aggregate(
            listOf(
                event("realm-1", "target-key", "relationship-1", UnlinkReason.SPAM),
                event("realm-1", "target-key", "relationship-2", UnlinkReason.SPAM),
                event("realm-1", "target-key", "relationship-3", UnlinkReason.SPAM),
            )
        ).single()

        assertEquals(ModerationAction.RESTRICT_INVITES, ComplaintModerator.recommendedAction(aggregate))
        assertTrue(ComplaintModerator.createRestriction(aggregate)?.action == ModerationAction.RESTRICT_INVITES)
    }

    private fun event(
        realmId: String,
        targetPublicKey: String,
        relationshipId: String,
        reason: UnlinkReason,
    ): ComplaintEvent =
        ComplaintEvent(
            complaintId = "complaint-$relationshipId",
            realmId = realmId,
            targetPublicKey = targetPublicKey,
            sourceRelationshipId = relationshipId,
            reason = reason,
            createdAtEpochMillis = 1,
        )
}
