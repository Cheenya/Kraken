package com.disser.kraken.courier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourierScoreTest {
    @Test
    fun dailyAggregationHidesExactTimestamps() {
        val snapshot = CourierScoreCalculator.aggregate(
            listOf(
                CourierScoreEvent("2026-05-17", forwardedCount = 2, confirmedUsefulRelayCount = 1, relayWindows = 1, ecoRelayBonus = 0),
                CourierScoreEvent("2026-05-17", forwardedCount = 3, confirmedUsefulRelayCount = 0, relayWindows = 2, ecoRelayBonus = 1),
            )
        )

        assertEquals(1, snapshot.summaries.size)
        assertEquals(5, snapshot.summaries.first().forwardedCount)
        assertEquals("2026-05-17", snapshot.summaries.first().dayBucket)
    }

    @Test
    fun courierScoreEventHasNoLocationRecipientRouteOrTimestampFields() {
        val fields = CourierScoreEvent::class.java.declaredFields.map { it.name.lowercase() }.toSet()

        assertFalse(fields.any { it.contains("gps") || it.contains("location") })
        assertFalse(fields.any { it.contains("recipient") || it.contains("route") })
        assertFalse(fields.any { it.contains("timestamp") || it.contains("epoch") })
    }

    @Test
    fun courierScoreIsSeparateFromRelayReliability() {
        val snapshot = CourierScoreCalculator.aggregate(
            listOf(CourierScoreEvent("2026-05-17", forwardedCount = 1, confirmedUsefulRelayCount = 1, relayWindows = 1, ecoRelayBonus = 1))
        )
        val reliability = RelayReliabilityScore(successfulSessions = 3, failedSessions = 1)

        assertEquals(6, snapshot.localScore)
        assertEquals(75, reliability.reliabilityPercent)
    }

    @Test
    fun noLeaderboardModelIsIntroduced() {
        val snapshotFields = CourierScoreSnapshot::class.java.declaredFields.map { it.name.lowercase() }

        assertTrue(snapshotFields.none { it.contains("leaderboard") || it.contains("global") })
    }
}
