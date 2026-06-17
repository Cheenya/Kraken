package com.disser.kraken.relationship

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ComplaintCategory {
    UNWANTED_MESSAGES,
    SPAM,
    THREAT_PRESSURE_OR_ETHICS_ABUSE,
    GOVERNANCE_ABUSE,
    OTHER,
}

@Serializable
data class ComplaintAggregate(
    @SerialName("realm_id")
    val realmId: String,
    @SerialName("target_public_key")
    val targetPublicKey: String,
    val category: ComplaintCategory,
    @SerialName("complaint_count")
    val complaintCount: Int,
    @SerialName("source_relationship_ids")
    val sourceRelationshipIds: Set<String>,
)

@Serializable
enum class ModerationAction {
    NONE,
    WATCH,
    RESTRICT_INVITES,
    RESTRICT_POSTING,
    REMOVE_FROM_REALM,
}

@Serializable
data class RestrictionState(
    @SerialName("target_public_key")
    val targetPublicKey: String,
    @SerialName("realm_id")
    val realmId: String,
    val action: ModerationAction,
    val reason: ComplaintCategory,
)

@Serializable
data class GovernanceEscalation(
    @SerialName("realm_id")
    val realmId: String,
    @SerialName("target_public_key")
    val targetPublicKey: String,
    val category: ComplaintCategory,
    @SerialName("aggregate_count")
    val aggregateCount: Int,
)

object ComplaintModerator {
    fun aggregate(events: List<ComplaintEvent>): List<ComplaintAggregate> =
        events.groupBy { event ->
            Triple(event.realmId, event.targetPublicKey, event.reason.toComplaintCategory())
        }.map { (key, grouped) ->
            ComplaintAggregate(
                realmId = key.first,
                targetPublicKey = key.second,
                category = key.third,
                complaintCount = grouped.size,
                sourceRelationshipIds = grouped.map { it.sourceRelationshipId }.toSet(),
            )
        }.sortedWith(compareByDescending<ComplaintAggregate> { it.complaintCount }.thenBy { it.targetPublicKey })

    fun recommendedAction(aggregate: ComplaintAggregate): ModerationAction =
        when {
            aggregate.complaintCount <= 1 -> ModerationAction.WATCH
            aggregate.category == ComplaintCategory.SPAM && aggregate.complaintCount >= 3 -> ModerationAction.RESTRICT_INVITES
            aggregate.category == ComplaintCategory.THREAT_PRESSURE_OR_ETHICS_ABUSE && aggregate.complaintCount >= 2 -> ModerationAction.RESTRICT_POSTING
            else -> ModerationAction.WATCH
        }

    fun createRestriction(aggregate: ComplaintAggregate): RestrictionState? {
        val action = recommendedAction(aggregate)
        if (action == ModerationAction.WATCH) return null
        return RestrictionState(
            targetPublicKey = aggregate.targetPublicKey,
            realmId = aggregate.realmId,
            action = action,
            reason = aggregate.category,
        )
    }

    fun governanceEscalation(aggregate: ComplaintAggregate): GovernanceEscalation? =
        if (aggregate.category == ComplaintCategory.GOVERNANCE_ABUSE) {
            GovernanceEscalation(
                realmId = aggregate.realmId,
                targetPublicKey = aggregate.targetPublicKey,
                category = aggregate.category,
                aggregateCount = aggregate.complaintCount,
            )
        } else {
            null
        }

    fun UnlinkReason.toComplaintCategory(): ComplaintCategory =
        when (this) {
            UnlinkReason.UNWANTED_MESSAGES -> ComplaintCategory.UNWANTED_MESSAGES
            UnlinkReason.SPAM -> ComplaintCategory.SPAM
            UnlinkReason.THREAT_PRESSURE_OR_ETHICS_ABUSE -> ComplaintCategory.THREAT_PRESSURE_OR_ETHICS_ABUSE
            UnlinkReason.OTHER -> ComplaintCategory.OTHER
            UnlinkReason.ENDED_INTERACTION -> ComplaintCategory.OTHER
        }
}
