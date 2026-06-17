package com.disser.kraken.courier

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CourierScoreEvent(
    @SerialName("day_bucket")
    val dayBucket: String,
    @SerialName("forwarded_count")
    val forwardedCount: Int,
    @SerialName("confirmed_useful_relay_count")
    val confirmedUsefulRelayCount: Int,
    @SerialName("relay_windows")
    val relayWindows: Int,
    @SerialName("eco_relay_bonus")
    val ecoRelayBonus: Int,
)

@Serializable
data class CourierDailySummary(
    @SerialName("day_bucket")
    val dayBucket: String,
    @SerialName("forwarded_count")
    val forwardedCount: Int,
    @SerialName("confirmed_useful_relay_count")
    val confirmedUsefulRelayCount: Int,
    @SerialName("relay_windows")
    val relayWindows: Int,
    @SerialName("eco_relay_bonus")
    val ecoRelayBonus: Int,
)

@Serializable
data class CourierScoreSnapshot(
    val summaries: List<CourierDailySummary>,
    @SerialName("streak_days")
    val streakDays: Int,
    @SerialName("local_score")
    val localScore: Int,
)

@Serializable
data class RelayReliabilityScore(
    @SerialName("successful_sessions")
    val successfulSessions: Int,
    @SerialName("failed_sessions")
    val failedSessions: Int,
) {
    val reliabilityPercent: Int
        get() {
            val total = successfulSessions + failedSessions
            return if (total == 0) 0 else (successfulSessions * 100) / total
        }
}

object CourierScoreCalculator {
    fun aggregate(events: List<CourierScoreEvent>): CourierScoreSnapshot {
        val summaries = events
            .groupBy { it.dayBucket }
            .toSortedMap()
            .map { (day, dayEvents) ->
                CourierDailySummary(
                    dayBucket = day,
                    forwardedCount = dayEvents.sumOf { it.forwardedCount },
                    confirmedUsefulRelayCount = dayEvents.sumOf { it.confirmedUsefulRelayCount },
                    relayWindows = dayEvents.sumOf { it.relayWindows },
                    ecoRelayBonus = dayEvents.sumOf { it.ecoRelayBonus },
                )
            }
        val score = summaries.sumOf { summary ->
            summary.forwardedCount +
                summary.confirmedUsefulRelayCount * 3 +
                summary.relayWindows +
                summary.ecoRelayBonus
        }
        return CourierScoreSnapshot(
            summaries = summaries,
            streakDays = contiguousStreakDays(summaries.map { it.dayBucket }),
            localScore = score,
        )
    }

    private fun contiguousStreakDays(dayBuckets: List<String>): Int {
        if (dayBuckets.isEmpty()) return 0
        return dayBuckets.distinct().size
    }
}
