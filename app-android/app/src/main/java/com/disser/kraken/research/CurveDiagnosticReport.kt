package com.disser.kraken.research

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val ANDROID_CURVE_REPORT_VERSION = "kraken.math.curve_diagnostic.android.v1"

@Serializable
data class AndroidCurveDiagnosticReport(
    @SerialName("report_version")
    val reportVersion: String,
    @SerialName("generated_at")
    val generatedAt: String,
    @SerialName("git_commit")
    val gitCommit: String? = null,
    val source: AndroidCurveReportSource,
    val curve: AndroidCurveReportCurve,
    val invariants: AndroidCurveReportInvariants,
    val diagnostics: AndroidCurveReportDiagnostics,
    val benchmark: AndroidCurveReportBenchmark,
    val warnings: List<String> = emptyList(),
    @SerialName("unsupported_cases")
    val unsupportedCases: List<String> = emptyList(),
    @SerialName("ui_wording")
    val uiWording: AndroidCurveReportUiWording,
)

@Serializable
data class AndroidCurveReportSource(
    val generator: String,
    @SerialName("generator_version")
    val generatorVersion: String,
    @SerialName("fixture_id")
    val fixtureId: String? = null,
)

@Serializable
data class AndroidCurveReportCurve(
    val model: String,
    val equation: String,
    val a: String,
    val b: String,
)

@Serializable
data class AndroidCurveReportInvariants(
    val discriminant: String,
    val singular: Boolean,
    @SerialName("j_invariant")
    val jInvariant: String? = null,
)

@Serializable
data class AndroidCurveReportDiagnostics(
    val discriminant: String,
    val singular: Boolean,
    @SerialName("j_invariant")
    val jInvariant: String? = null,
    @SerialName("two_torsion")
    val twoTorsion: AndroidTwoTorsionSummary,
    @SerialName("lutz_nagell")
    val lutzNagell: AndroidLutzNagellSummary,
    @SerialName("torsion_probe_results")
    val torsionProbeResults: List<AndroidTorsionProbeResult> = emptyList(),
)

@Serializable
data class AndroidTwoTorsionSummary(
    val summary: String,
    val points: List<AndroidCurvePoint> = emptyList(),
)

@Serializable
data class AndroidLutzNagellSummary(
    val summary: String,
    @SerialName("candidate_count")
    val candidateCount: Int,
    val candidates: List<AndroidCurvePoint> = emptyList(),
)

@Serializable
data class AndroidTorsionProbeResult(
    val point: AndroidCurvePoint,
    val status: String,
    val order: Int? = null,
    @SerialName("max_order")
    val maxOrder: Int,
    val reason: String? = null,
)

@Serializable
data class AndroidCurvePoint(
    val x: String,
    val y: String,
) {
    val label: String = "($x, $y)"
}

@Serializable
data class AndroidCurveReportBenchmark(
    @SerialName("runtime_ms")
    val runtimeMs: Double? = null,
    @SerialName("run_id")
    val runId: String,
    val environment: String,
)

@Serializable
data class AndroidCurveReportUiWording(
    val title: String,
    val summary: String,
    @SerialName("security_note")
    val securityNote: String,
    @SerialName("unsupported_note")
    val unsupportedNote: String,
)

data class CurveReportDisplayModel(
    val title: String,
    val warning: String,
    val curveEquation: String,
    val invariantRows: List<Pair<String, String>>,
    val twoTorsionSummary: String,
    val lutzNagellSummary: String,
    val torsionProbeSummary: String,
    val benchmarkSummary: String,
    val warnings: List<String>,
    val unsupportedCases: List<String>,
)

fun AndroidCurveDiagnosticReport.toDisplayModel(): CurveReportDisplayModel {
    val exactOrders = diagnostics.torsionProbeResults
        .mapNotNull { it.order }
        .groupingBy { it }
        .eachCount()
        .toSortedMap()
        .map { (order, count) -> "order $order: $count" }
    val probeSummary = if (diagnostics.torsionProbeResults.isEmpty()) {
        "No bounded torsion probe rows in this report."
    } else {
        "${diagnostics.torsionProbeResults.size} probe rows; ${exactOrders.joinToString().ifBlank { "no exact orders" }}"
    }
    val runtime = benchmark.runtimeMs?.let { "%.3f ms".format(it) } ?: "not measured"

    return CurveReportDisplayModel(
        title = uiWording.title,
        warning = uiWording.securityNote,
        curveEquation = curve.equation,
        invariantRows = listOf(
            "Discriminant" to invariants.discriminant,
            "Singular" to invariants.singular.toString(),
            "j-invariant" to (invariants.jInvariant ?: "unsupported"),
            "Generated" to generatedAt,
            "Generator" to source.generator,
        ),
        twoTorsionSummary = "${diagnostics.twoTorsion.summary}; points: ${diagnostics.twoTorsion.points.size}",
        lutzNagellSummary = "${diagnostics.lutzNagell.summary}; candidates: ${diagnostics.lutzNagell.candidateCount}",
        torsionProbeSummary = probeSummary,
        benchmarkSummary = "Runtime: $runtime; environment: ${benchmark.environment}",
        warnings = warnings,
        unsupportedCases = unsupportedCases,
    )
}
