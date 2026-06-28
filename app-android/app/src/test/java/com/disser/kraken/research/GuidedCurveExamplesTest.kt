package com.disser.kraken.research

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedCurveExamplesTest {
    private val manifestFile = File("src/main/assets/research/examples/manifest.json")
    private val manifest: GuidedCurveExampleManifest =
        (GuidedCurveExampleRepository.parseManifest(manifestFile.readText()) as GuidedCurveExamplesLoadResult.Success).manifest

    @Test
    fun manifestParsesAndUsesAndroidReportVersion() {
        assertEquals(ANDROID_CURVE_REPORT_VERSION, manifest.reportVersion)
        assertTrue(manifest.researchWarning.contains("диагност", ignoreCase = true))
        assertTrue(manifest.examples.isNotEmpty())
    }

    @Test
    fun examplesCoverAllRequiredTiers() {
        assertTrue(manifest.examples.any { it.category == GuidedCurveExampleCategory.TEACHING })
        assertTrue(manifest.examples.any { it.category == GuidedCurveExampleCategory.VALIDATION })
        assertTrue(manifest.examples.any { it.category == GuidedCurveExampleCategory.RESEARCH_SCALE })
    }

    @Test
    fun teachingExamplesAreMarkedTeachingOnly() {
        val teaching = manifest.examples.filter { it.category == GuidedCurveExampleCategory.TEACHING }

        assertTrue(teaching.isNotEmpty())
        assertTrue(teaching.all { it.teachingOnly })
    }

    @Test
    fun researchScaleExamplesKeepDiagnosticScope() {
        val researchScale = manifest.examples.filter { it.category == GuidedCurveExampleCategory.RESEARCH_SCALE }

        assertTrue(researchScale.isNotEmpty())
        assertTrue(researchScale.all { !it.cryptographicSafetyClaim })
        assertTrue(researchScale.all { it.validationStatus == "SageMath direct match" })
        assertTrue(researchScale.all { it.teachingOnly.not() })
        assertTrue(researchScale.all { it.caveat?.contains("SageMath") == true })
    }

    @Test
    fun researchScaleIncludesMultipleValidatedLargeCoefficientReports() {
        val researchScale = manifest.examples.filter { it.category == GuidedCurveExampleCategory.RESEARCH_SCALE }

        assertTrue(researchScale.size >= 3)
        assertTrue(researchScale.count { it.validationStatus == "SageMath direct match" } >= 3)
        assertTrue(researchScale.all { it.assetPath != null })
        assertTrue(researchScale.any { it.coefficientSize.contains("128-bit-ish") })
        assertTrue(researchScale.all { it.b != "0" })
    }

    @Test
    fun noExampleClaimsCryptographicSafety() {
        val forbiddenClaims = listOf(
            "публичная криптографическая стойкость",
            "готовый криптографический контур",
            "усиленная криптография",
        )
        val manifestText = manifestFile.readText().lowercase()

        forbiddenClaims.forEach { phrase ->
            assertFalse("В manifest найдено лишнее обещание: $phrase", manifestText.contains(phrase))
        }
        assertTrue(manifest.examples.all { !it.cryptographicSafetyClaim })
    }

    @Test
    fun selectingExampleFillsCoefficientDisplayModel() {
        val researchScale = manifest.examples.first { it.category == GuidedCurveExampleCategory.RESEARCH_SCALE }
        val input = researchScale.toInput().getOrThrow()

        assertEquals(researchScale.a, input.a.toString())
        assertEquals(researchScale.b, input.b.toString())
        assertTrue(input.shortName.contains(researchScale.a))
        assertTrue(input.shortName.contains(researchScale.b))
    }

    @Test
    fun visibleExamplesAvoidSingularOrBZeroTeachingShortcuts() {
        assertTrue(manifest.examples.none { it.exampleId == "teaching_singular_zero_curve" })
        assertTrue(manifest.examples.none { it.b == "0" })

        val teaching = manifest.examples.first { it.category == GuidedCurveExampleCategory.TEACHING }
        assertEquals("2", teaching.a)
        assertEquals("3", teaching.b)
        assertTrue(teaching.expectedResult.contains("Nonsingular"))
    }

    @Test
    fun sageDirectMatchLabelOnlyAppearsOnBundledDirectMatchReports() {
        val directMatchFixtureIds = setOf(
            "no_two_torsion_x3_plus_x_plus_1",
            "lc32_prime_offsets_no_two_torsion",
            "lc128_large_a_small_b_no_two_torsion",
            "lcstruct_lutz_large_discriminant_stress",
        )
        val directMatches = manifest.examples.filter { it.validationStatus == "SageMath direct match" }

        assertTrue(directMatches.isNotEmpty())
        directMatches.forEach { example ->
            val assetPath = example.assetPath
            assertNotNull(assetPath)
            val rawJson = File("src/main/assets/$assetPath").readText()
            val report = (CurveReportRepository.parseReport(rawJson) as CurveReportLoadResult.Success).report
            assertTrue("Unexpected direct match fixture: ${report.source.fixtureId}", report.source.fixtureId in directMatchFixtureIds)
        }
    }

    @Test
    fun largeCoefficientSummaryUsesValidatedCorpusNumbers() {
        val summary = manifest.largeCoefficientValidation

        assertNotNull(summary)
        requireNotNull(summary)
        assertEquals(20, summary.curveCount)
        assertEquals(20, summary.sageCompared)
        assertEquals(20, summary.directMatches)
        assertEquals(0, summary.mismatches)
        assertEquals(5, summary.benchmarkRuns)
        assertEquals(22.8673, summary.medianTotalRuntimeMs, 0.0001)
        assertEquals(24.1665, summary.p95TotalRuntimeMs, 0.0001)
        assertTrue(summary.caveat.contains("рациональных кривых"))
        assertTrue(summary.caveat.contains("SageMath"))
    }

    @Test
    fun oldPendingResearchScaleExampleIsNotTheOnlyResearchScaleExample() {
        val researchScale = manifest.examples.filter { it.category == GuidedCurveExampleCategory.RESEARCH_SCALE }

        assertTrue(researchScale.size > 1)
        assertFalse(researchScale.any { it.validationStatus.contains("pending", ignoreCase = true) })
    }
}
