package com.disser.kraken.research

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchDiagnosticServiceTest {
    @Test
    fun singularCurveIsDetected() {
        val result = ResearchDiagnosticService.evaluate(CurveInput(BigInteger.ZERO, BigInteger.ZERO))

        assertFalse(result.nonsingular)
        assertEquals("SINGULAR", result.classificationCase)
    }

    @Test
    fun threeIntegerTwoTorsionRootsMapToA6Placeholder() {
        val result = ResearchDiagnosticService.evaluate(
            CurveInput(BigInteger.valueOf(-4), BigInteger.ZERO)
        )

        assertTrue(result.nonsingular)
        assertEquals(3, result.twoTorsionRootCount)
        assertEquals("A6", result.classificationCase)
    }

    @Test
    fun integerThreeTorsionIndicatorWithOneTwoTorsionRootMapsToA2() {
        val result = ResearchDiagnosticService.evaluate(
            CurveInput(BigInteger.ZERO, BigInteger.ONE)
        )

        assertTrue(result.nonsingular)
        assertTrue(result.hasThreeTorsionIndicator)
        assertEquals(1, result.twoTorsionRootCount)
        assertEquals("A2", result.classificationCase)
    }

    @Test
    fun inputParserRejectsNonIntegerText() {
        val parsed = ResearchDiagnosticService.parseCurveInput("abc", "1")

        assertTrue(parsed.isFailure)
    }

    @Test
    fun diagnosticResultWarnsThatItIsNotProductionEncryption() {
        val result = ResearchDiagnosticService.evaluate(CurveInput(BigInteger.ONE, BigInteger.ONE))

        assertTrue(result.diagnosticOnlyWarning.contains("Diagnostic-only"))
        assertTrue(result.diagnosticOnlyWarning.contains("not production encryption"))
    }

    @Test
    fun largeDiscriminantStressInputIsSizeGuardedAndReturnsQuickly() {
        val start = System.nanoTime()
        val result = ResearchDiagnosticService.evaluate(
            CurveInput(
                a = BigInteger("1000000000039"),
                b = BigInteger("1000000000061"),
            )
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("large diagnostic should not block the UI thread", elapsedMs < 500)
        assertTrue(result.nonsingular)
        assertFalse(result.localDiagnosticSupported)
        assertEquals("SIZE_GUARDED", result.classificationCase)
        assertTrue(result.unsupportedReasons.any { it.contains("divisor enumeration") })
    }

    @Test
    fun veryLargeCoefficientInputSkipsThreeTorsionIndicatorScan() {
        val result = ResearchDiagnosticService.evaluate(
            CurveInput(
                a = BigInteger("170141183460469231731687303715884105727"),
                b = BigInteger.ONE,
            )
        )

        assertTrue(result.nonsingular)
        assertFalse(result.localDiagnosticSupported)
        assertEquals("SIZE_GUARDED", result.classificationCase)
        assertFalse(result.hasThreeTorsionIndicator)
        assertTrue(result.unsupportedReasons.any { it.contains("3-torsion indicator") })
    }
}
