package com.disser.kraken.research

import android.content.Context
import java.math.BigInteger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString

@Serializable
data class GuidedCurveExampleManifest(
    @SerialName("report_version")
    val reportVersion: String,
    @SerialName("generated_at")
    val generatedAt: String,
    @SerialName("research_warning")
    val researchWarning: String,
    @SerialName("large_coefficient_validation")
    val largeCoefficientValidation: LargeCoefficientValidationSummary? = null,
    val examples: List<GuidedCurveExample>,
)

@Serializable
data class LargeCoefficientValidationSummary(
    val title: String,
    @SerialName("curve_count")
    val curveCount: Int,
    @SerialName("sage_compared")
    val sageCompared: Int,
    @SerialName("direct_matches")
    val directMatches: Int,
    val mismatches: Int,
    @SerialName("benchmark_runs")
    val benchmarkRuns: Int,
    @SerialName("median_total_runtime_ms")
    val medianTotalRuntimeMs: Double,
    @SerialName("p95_total_runtime_ms")
    val p95TotalRuntimeMs: Double,
    val caveat: String,
)

@Serializable
data class GuidedCurveExample(
    @SerialName("example_id")
    val exampleId: String,
    val category: GuidedCurveExampleCategory,
    val title: String,
    @SerialName("asset_path")
    val assetPath: String? = null,
    val a: String,
    val b: String,
    val equation: String,
    @SerialName("coefficient_size")
    val coefficientSize: String,
    @SerialName("validation_status")
    val validationStatus: String,
    @SerialName("expected_result")
    val expectedResult: String,
    val why: String,
    @SerialName("teaching_only")
    val teachingOnly: Boolean,
    @SerialName("production_crypto_claim")
    val productionCryptoClaim: Boolean,
    val caveat: String? = null,
) {
    fun toInput(): Result<CurveInput> =
        runCatching {
            CurveInput(
                a = BigInteger(a.trim()),
                b = BigInteger(b.trim()),
            )
        }
}

@Serializable
enum class GuidedCurveExampleCategory(val label: String) {
    TEACHING("Учебные"),
    VALIDATION("Валидация"),
    RESEARCH_SCALE("Исследовательский масштаб"),
}

sealed class GuidedCurveExamplesLoadResult {
    data class Success(val manifest: GuidedCurveExampleManifest) : GuidedCurveExamplesLoadResult()
    data class Error(val reason: String) : GuidedCurveExamplesLoadResult()
}

object GuidedCurveExampleRepository {
    private const val MANIFEST_ASSET_PATH = "research/examples/manifest.json"

    fun loadManifest(context: Context): GuidedCurveExamplesLoadResult =
        runCatching {
            context.assets.open(MANIFEST_ASSET_PATH).bufferedReader().use { it.readText() }
        }.fold(
            onSuccess = ::parseManifest,
            onFailure = { GuidedCurveExamplesLoadResult.Error("Не удалось прочитать manifest примеров: ${it.message}") },
        )

    fun parseManifest(rawJson: String): GuidedCurveExamplesLoadResult =
        try {
            val manifest = CurveReportRepository.json.decodeFromString<GuidedCurveExampleManifest>(rawJson)
            validate(manifest)
        } catch (exception: IllegalArgumentException) {
            GuidedCurveExamplesLoadResult.Error(exception.message ?: "Проверка набора примеров завершилась ошибкой.")
        } catch (exception: SerializationException) {
            GuidedCurveExamplesLoadResult.Error("Manifest примеров повреждён или не содержит обязательные поля.")
        }

    private fun validate(manifest: GuidedCurveExampleManifest): GuidedCurveExamplesLoadResult {
        if (manifest.reportVersion != ANDROID_CURVE_REPORT_VERSION) {
            return GuidedCurveExamplesLoadResult.Error("Неподдерживаемая версия набора примеров: ${manifest.reportVersion}")
        }
        if (!manifest.researchWarning.contains("Диагност", ignoreCase = true)) {
            return GuidedCurveExamplesLoadResult.Error("Набор примеров должен содержать диагностическое описание.")
        }
        if (manifest.examples.any { it.productionCryptoClaim }) {
            return GuidedCurveExamplesLoadResult.Error("Примеры не должны заявлять криптографическую безопасность.")
        }
        val sageDirectMatchExamples = manifest.examples
            .filter { it.validationStatus == "совпадает с SageMath" }
        if (sageDirectMatchExamples.any { it.assetPath == null }) {
            return GuidedCurveExamplesLoadResult.Error("Примеры со сверкой SageMath должны ссылаться на встроенные отчёты.")
        }
        return GuidedCurveExamplesLoadResult.Success(manifest)
    }
}
