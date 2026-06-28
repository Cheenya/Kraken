package com.disser.kraken.research

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed class CurveReportLoadResult {
    data class Success(val report: AndroidCurveDiagnosticReport) : CurveReportLoadResult()
    data class Error(val reason: String) : CurveReportLoadResult()
}

object CurveReportRepository {
    private const val SAMPLE_ASSET_PATH = "research/sample_curve_diagnostic_report.json"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun loadBundledSampleReport(context: Context): CurveReportLoadResult =
        loadReportAsset(context, SAMPLE_ASSET_PATH)

    fun loadReportAsset(context: Context, assetPath: String): CurveReportLoadResult {
        if (!assetPath.startsWith("research/")) {
            return CurveReportLoadResult.Error("Report asset path is outside the research bundle.")
        }
        return runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.fold(
            onSuccess = ::parseReport,
            onFailure = { CurveReportLoadResult.Error("Bundled report could not be read: ${it.message}") },
        )
    }

    fun parseReport(rawJson: String): CurveReportLoadResult =
        try {
            val report = json.decodeFromString<AndroidCurveDiagnosticReport>(rawJson)
            validate(report)
        } catch (exception: IllegalArgumentException) {
            CurveReportLoadResult.Error(exception.message ?: "Report validation failed.")
        } catch (exception: SerializationException) {
            CurveReportLoadResult.Error("Report JSON is invalid or missing required fields.")
        }

    private fun validate(report: AndroidCurveDiagnosticReport): CurveReportLoadResult {
        if (report.reportVersion != ANDROID_CURVE_REPORT_VERSION) {
            return CurveReportLoadResult.Error("Unsupported report version: ${report.reportVersion}")
        }
        if (!report.uiWording.securityNote.contains("диагност", ignoreCase = true)) {
            return CurveReportLoadResult.Error("Report must include diagnostic context wording.")
        }
        return CurveReportLoadResult.Success(report)
    }
}
