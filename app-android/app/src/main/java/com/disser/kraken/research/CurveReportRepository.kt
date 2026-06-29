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
            return CurveReportLoadResult.Error("Путь к отчёту находится вне исследовательского набора.")
        }
        return runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.fold(
            onSuccess = ::parseReport,
            onFailure = { CurveReportLoadResult.Error("Не удалось прочитать встроенный отчёт: ${it.message}") },
        )
    }

    fun parseReport(rawJson: String): CurveReportLoadResult =
        try {
            val report = json.decodeFromString<AndroidCurveDiagnosticReport>(rawJson)
            validate(report)
        } catch (exception: IllegalArgumentException) {
            CurveReportLoadResult.Error(exception.message ?: "Проверка отчёта завершилась ошибкой.")
        } catch (exception: SerializationException) {
            CurveReportLoadResult.Error("JSON отчёта повреждён или не содержит обязательные поля.")
        }

    private fun validate(report: AndroidCurveDiagnosticReport): CurveReportLoadResult {
        if (report.reportVersion != ANDROID_CURVE_REPORT_VERSION) {
            return CurveReportLoadResult.Error("Неподдерживаемая версия отчёта: ${report.reportVersion}")
        }
        if (!report.uiWording.securityNote.contains("Диагност", ignoreCase = true)) {
            return CurveReportLoadResult.Error("Отчёт должен содержать диагностическое описание.")
        }
        return CurveReportLoadResult.Success(report)
    }
}
