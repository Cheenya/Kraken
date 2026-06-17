package com.disser.kraken.research

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveReportRepositoryTest {
    @Test
    fun bundledSampleReportParsesFromAssetJson() {
        val rawJson = File("src/main/assets/research/sample_curve_diagnostic_report.json").readText()

        val result = CurveReportRepository.parseReport(rawJson)

        assertTrue(result is CurveReportLoadResult.Success)
        val report = (result as CurveReportLoadResult.Success).report
        assertEquals(ANDROID_CURVE_REPORT_VERSION, report.reportVersion)
        assertEquals("disser_messenger.math_core", report.source.generator)
        assertEquals("-1", report.curve.a)
        assertEquals("0", report.curve.b)
    }

    @Test
    fun reportVersionValidationRejectsUnsupportedVersion() {
        val rawJson = File("src/main/assets/research/sample_curve_diagnostic_report.json").readText()
        val modified = rawJson.replace(ANDROID_CURVE_REPORT_VERSION, "unsupported.version")

        val result = CurveReportRepository.parseReport(modified)

        assertTrue(result is CurveReportLoadResult.Error)
        assertTrue((result as CurveReportLoadResult.Error).reason.contains("Unsupported report version"))
    }

    @Test
    fun missingRequiredFieldsFailSafely() {
        val result = CurveReportRepository.parseReport("""{"report_version":"$ANDROID_CURVE_REPORT_VERSION"}""")

        assertTrue(result is CurveReportLoadResult.Error)
    }

    @Test
    fun displayModelKeepsDiagnosticOnlyWarningVisible() {
        val rawJson = File("src/main/assets/research/sample_curve_diagnostic_report.json").readText()
        val report = (CurveReportRepository.parseReport(rawJson) as CurveReportLoadResult.Success).report

        val displayModel = report.toDisplayModel()

        assertTrue(displayModel.warning.contains("Diagnostic-only"))
        assertTrue(displayModel.warning.contains("not production encryption"))
        assertTrue(displayModel.benchmarkSummary.contains("offline-python"))
    }

    @Test
    fun sampleJsonCanBeLoadedByStandardJsonParser() {
        val rawJson = File("src/main/assets/research/sample_curve_diagnostic_report.json").readText()
        val parsed = Json.parseToJsonElement(rawJson).jsonObject

        assertEquals(ANDROID_CURVE_REPORT_VERSION, parsed["report_version"].toString().trim('"'))
    }

    @Test
    fun bundledExampleReportsParseFromAssetJson() {
        val manifestRaw = File("src/main/assets/research/examples/manifest.json").readText()
        val manifest = (GuidedCurveExampleRepository.parseManifest(manifestRaw) as GuidedCurveExamplesLoadResult.Success).manifest

        manifest.examples.mapNotNull { it.assetPath }.forEach { assetPath ->
            val rawJson = File("src/main/assets/$assetPath").readText()
            val result = CurveReportRepository.parseReport(rawJson)

            assertTrue("Report did not parse: $assetPath", result is CurveReportLoadResult.Success)
        }
    }
}
