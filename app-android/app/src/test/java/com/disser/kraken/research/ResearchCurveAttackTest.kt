package com.disser.kraken.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchCurveAttackTest {
    @Test
    fun challengePointsAreOnCurve() {
        StartupResearchCurveAttack.candidateChallenges.forEach { challenge ->
            assertTrue(StartupResearchCurveAttack.isOnCurve(challenge.basePoint))
            assertTrue(StartupResearchCurveAttack.isOnCurve(challenge.publicPoint))
        }
    }

    @Test
    fun validationGateRunsUntilWeakCandidateIsFound() {
        val profile = StartupResearchCurveAttack.TestProfile
        val result = StartupResearchCurveAttack.run(profile)

        assertEquals("weak_candidate_found", result.status)
        assertEquals(profile.weakSecret, result.recoveredSecret)
        assertEquals(profile.passCandidateCount + 1, result.testedChallenges)
        val expectedChecked = profile.passCandidateCount * profile.candidateBudgetPerChallenge + profile.weakSecret
        assertEquals(expectedChecked, result.checkedCandidates)
        assertTrue(result.validationDecision.startsWith("REJECT"))
        assertEquals(1, result.validationRejectedChallenges)
        assertTrue(result.caveat.contains("does not attack production Kraken identities"))
    }

    @Test
    fun progressFormatterUsesCandidateCounts() {
        assertEquals("742913/1000000", formatResearchAttackCandidateProgress(742_913, 1_000_000))
    }
}
