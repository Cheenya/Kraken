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
    @SerialName("cryptographic_safety_claim")
    val cryptographicSafetyClaim: Boolean,
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
    TEACHING("Teaching"),
    VALIDATION("Validation"),
    RESEARCH_SCALE("Research-scale"),
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
            onFailure = { GuidedCurveExamplesLoadResult.Error("Guided examples manifest could not be read: ${it.message}") },
        )

    fun parseManifest(rawJson: String): GuidedCurveExamplesLoadResult =
        try {
            val manifest = CurveReportRepository.json.decodeFromString<GuidedCurveExampleManifest>(rawJson)
            validate(manifest)
        } catch (exception: IllegalArgumentException) {
            GuidedCurveExamplesLoadResult.Error(exception.message ?: "Guided examples validation failed.")
        } catch (exception: SerializationException) {
            GuidedCurveExamplesLoadResult.Error("Guided examples manifest JSON is invalid or missing required fields.")
        }

    private fun validate(manifest: GuidedCurveExampleManifest): GuidedCurveExamplesLoadResult {
        if (manifest.reportVersion != ANDROID_CURVE_REPORT_VERSION) {
            return GuidedCurveExamplesLoadResult.Error("Unsupported guided examples version: ${manifest.reportVersion}")
        }
        if (!manifest.researchWarning.contains("диагност", ignoreCase = true)) {
            return GuidedCurveExamplesLoadResult.Error("Guided examples must include diagnostic context wording.")
        }
        if (manifest.examples.any { it.cryptographicSafetyClaim }) {
            return GuidedCurveExamplesLoadResult.Error("Guided examples use diagnostic profile metadata only.")
        }
        val sageDirectMatchExamples = manifest.examples
            .filter { it.validationStatus == "SageMath direct match" }
        if (sageDirectMatchExamples.any { it.assetPath == null }) {
            return GuidedCurveExamplesLoadResult.Error("SageMath direct match examples must be backed by bundled reports.")
        }
        return GuidedCurveExamplesLoadResult.Success(manifest)
    }
}
