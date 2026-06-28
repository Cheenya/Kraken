package com.disser.kraken.crypto

import com.disser.kraken.nativecore.NativeAdamovaResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class AdamovaAdmissionAttackDemoRunnerTest {
    @Test
    fun attackDemoComparesNoPrecheckDiscriminantOnlyAndAdamovaGate() {
        val gate = ProductCryptoAdmissionGate(
            validator = ScenarioValidator(),
            now = { 1_700_000_000_000 },
        )
        var tick = 0L
        val runner = AdamovaAdmissionAttackDemoRunner(gate) {
            tick += 1_000_000
            tick
        }

        val report = runner.run()

        assertEquals(7, report.metrics.profilesTotal)
        assertEquals(7, report.metrics.weakProfilesTotal)
        assertEquals(6, report.metrics.acceptedWithoutPrecheck)
        assertEquals(5, report.metrics.acceptedByDiscriminantOnly)
        assertEquals(0, report.metrics.acceptedByAdamovaGate)
        assertEquals(7, report.metrics.rejectedByAdamovaGate)
        assertEquals(1, report.metrics.needsReferenceValidation)
        assertEquals(1, report.metrics.sizeGuarded)
        assertTrue(report.safeClaim.contains("weak experimental curve profiles"))
        assertTrue(report.claimBoundary.contains("profile admission behavior"))
        assertTrue(report.toMarkdown().contains("downgrade_to_weak_profile"))
        assertTrue(report.toJson().contains("\"packet_profile_mismatch\""))
    }

    @Test
    fun singularScenarioShowsDiscriminantOnlyAndAdamovaBothReject() {
        val gate = ProductCryptoAdmissionGate(
            validator = ScenarioValidator(),
            now = { 1_700_000_000_000 },
        )
        val report = AdamovaAdmissionAttackDemoRunner(gate) { 1_000_000 }.run()

        val singular = report.results.single { it.kind == AdamovaAttackScenarioKind.SINGULAR_CURVE_PROFILE }

        assertTrue(singular.noPrecheckAccepted)
        assertFalse(singular.discriminantOnlyAccepted)
        assertEquals(AdamovaAdmissionDecision.REJECT_SINGULAR, singular.adamovaDecision)
        assertFalse(singular.adamovaAccepted)
    }

    @Test
    fun torsionScenarioShowsDiscriminantOnlyMissesButAdamovaRejects() {
        val gate = ProductCryptoAdmissionGate(
            validator = ScenarioValidator(),
            now = { 1_700_000_000_000 },
        )
        val report = AdamovaAdmissionAttackDemoRunner(gate) { 1_000_000 }.run()

        val twoTorsion = report.results.single { it.kind == AdamovaAttackScenarioKind.TWO_TORSION_PROFILE }

        assertTrue(twoTorsion.noPrecheckAccepted)
        assertTrue(twoTorsion.discriminantOnlyAccepted)
        assertEquals(AdamovaAdmissionDecision.REJECT_SMALL_TORSION_RISK, twoTorsion.adamovaDecision)
        assertFalse(twoTorsion.adamovaAccepted)
    }

    private class ScenarioValidator : AdamovaNativeValidator {
        override fun status(): String = "scenario-validator-v1"

        override fun classify(a: BigInteger, b: BigInteger): NativeAdamovaResult =
            when {
                a == BigInteger.ZERO && b == BigInteger.ZERO ->
                    nativeResult(a, b, singular = true, classificationCase = "SINGULAR")
                a == BigInteger.valueOf(-1) && b == BigInteger.ZERO ->
                    nativeResult(a, b, twoTorsionRootCount = 3, classificationCase = "A6")
                a == BigInteger.ZERO && b == BigInteger.ONE ->
                    nativeResult(a, b, hasThreeTorsionIndicator = true, threeTorsionRootCount = 1, classificationCase = "A1")
                a.toString().length > 30 ->
                    nativeResult(a, b, classificationCase = "SIZE_GUARDED", earlyStopHit = true)
                else -> nativeResult(a, b, classificationCase = "A4")
            }

        private fun nativeResult(
            a: BigInteger,
            b: BigInteger,
            singular: Boolean = false,
            twoTorsionRootCount: Int = 0,
            threeTorsionRootCount: Int = 0,
            hasThreeTorsionIndicator: Boolean = false,
            classificationCase: String,
            earlyStopHit: Boolean = false,
        ): NativeAdamovaResult =
            NativeAdamovaResult(
                a = a,
                b = b,
                singular = singular,
                discriminant = BigInteger.ONE,
                twoTorsionRootCount = twoTorsionRootCount,
                twoTorsionRoots = emptyList(),
                threeTorsionRootCount = threeTorsionRootCount,
                threeTorsionRoots = emptyList(),
                hasThreeTorsionIndicator = hasThreeTorsionIndicator,
                hasThreeTorsionInconsistency = false,
                classificationCase = classificationCase,
                roots3CandidatesTotal = 0,
                roots3RejectedMod = 0,
                roots3RejectedBound = 0,
                roots3PassedFilters = 0,
                roots3ExactChecked = 0,
                roots3ExactZero = 0,
                roots3SquarecheckPass = 0,
                divisorCountA2 = 0,
                factorizationSteps = 0,
                xSquare = emptyList(),
                earlyStopHit = earlyStopHit,
            )
    }
}
