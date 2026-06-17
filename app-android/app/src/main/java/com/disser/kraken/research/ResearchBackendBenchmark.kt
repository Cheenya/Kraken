package com.disser.kraken.research

import java.math.BigInteger
import java.util.Locale
import kotlin.math.roundToLong

data class ResearchBackendBenchmarkCase(
    val id: String,
    val group: String,
    val input: CurveInput,
)

data class ResearchBackendTiming(
    val backend: String,
    val medianNs: Long,
    val minNs: Long,
    val maxNs: Long,
    val supported: Boolean,
    val classificationCase: String,
)

data class ResearchBackendBenchmarkRow(
    val case: ResearchBackendBenchmarkCase,
    val kotlinTiming: ResearchBackendTiming,
    val nativeTiming: ResearchBackendTiming,
) {
    val speedup: Double =
        if (nativeTiming.medianNs == 0L) 0.0 else kotlinTiming.medianNs.toDouble() / nativeTiming.medianNs.toDouble()
}

data class ResearchBackendBenchmarkReport(
    val warmupRuns: Int,
    val measuredRuns: Int,
    val rows: List<ResearchBackendBenchmarkRow>,
) {
    val kotlinMedianTotalNs: Long = rows.sumOf { it.kotlinTiming.medianNs }
    val nativeMedianTotalNs: Long = rows.sumOf { it.nativeTiming.medianNs }
    val totalSpeedup: Double =
        if (nativeMedianTotalNs == 0L) 0.0 else kotlinMedianTotalNs.toDouble() / nativeMedianTotalNs.toDouble()
    val comparableRows: List<ResearchBackendBenchmarkRow> =
        rows.filter { it.kotlinTiming.supported && it.nativeTiming.supported }
    val kotlinComparableMedianTotalNs: Long = comparableRows.sumOf { it.kotlinTiming.medianNs }
    val nativeComparableMedianTotalNs: Long = comparableRows.sumOf { it.nativeTiming.medianNs }
    val comparableSpeedup: Double =
        if (nativeComparableMedianTotalNs == 0L) 0.0 else {
            kotlinComparableMedianTotalNs.toDouble() / nativeComparableMedianTotalNs.toDouble()
        }

    fun toMarkdown(): String = buildString {
        appendLine("# Research Backend Benchmark")
        appendLine()
        appendLine("Diagnostic-only benchmark. This does not measure production message encryption.")
        appendLine()
        appendLine("- Warmup runs per backend/case: $warmupRuns")
        appendLine("- Measured runs per backend/case: $measuredRuns")
        appendLine("- Kotlin median total: ${formatMs(kotlinMedianTotalNs)} ms")
        appendLine("- C++ median total: ${formatMs(nativeMedianTotalNs)} ms")
        appendLine("- C++ speedup by summed medians: ${formatRatio(totalSpeedup)}x")
        appendLine("- Comparable exact rows: ${comparableRows.size}")
        appendLine("- Comparable exact C++ speedup: ${formatRatio(comparableSpeedup)}x")
        appendLine()
        appendLine("| Case | Group | Kotlin median ms | C++ median ms | Speedup | Kotlin case | C++ case |")
        appendLine("|---|---:|---:|---:|---:|---|---|")
        rows.forEach { row ->
            appendLine(
                "| ${row.case.id} | ${row.case.group} | ${formatMs(row.kotlinTiming.medianNs)} | " +
                    "${formatMs(row.nativeTiming.medianNs)} | ${formatRatio(row.speedup)}x | " +
                    "${row.kotlinTiming.classificationCase} | ${row.nativeTiming.classificationCase} |"
            )
        }
    }

    fun toJson(): String = buildString {
        append("{\n")
        append("  \"warning\": \"Diagnostic-only benchmark. This does not measure production message encryption.\",\n")
        append("  \"warmup_runs\": $warmupRuns,\n")
        append("  \"measured_runs\": $measuredRuns,\n")
        append("  \"kotlin_median_total_ns\": $kotlinMedianTotalNs,\n")
        append("  \"native_median_total_ns\": $nativeMedianTotalNs,\n")
        append("  \"native_speedup\": ${formatRatio(totalSpeedup)},\n")
        append("  \"comparable_exact_rows\": ${comparableRows.size},\n")
        append("  \"comparable_exact_native_speedup\": ${formatRatio(comparableSpeedup)},\n")
        append("  \"rows\": [\n")
        rows.forEachIndexed { index, row ->
            if (index > 0) append(",\n")
            append("    {\n")
            append("      \"case_id\": \"${json(row.case.id)}\",\n")
            append("      \"group\": \"${json(row.case.group)}\",\n")
            append("      \"a\": \"${json(row.case.input.a.toString())}\",\n")
            append("      \"b\": \"${json(row.case.input.b.toString())}\",\n")
            append("      \"kotlin_median_ns\": ${row.kotlinTiming.medianNs},\n")
            append("      \"native_median_ns\": ${row.nativeTiming.medianNs},\n")
            append("      \"native_speedup\": ${formatRatio(row.speedup)},\n")
            append("      \"kotlin_supported\": ${row.kotlinTiming.supported},\n")
            append("      \"native_supported\": ${row.nativeTiming.supported},\n")
            append("      \"kotlin_case\": \"${json(row.kotlinTiming.classificationCase)}\",\n")
            append("      \"native_case\": \"${json(row.nativeTiming.classificationCase)}\"\n")
            append("    }")
        }
        append("\n  ]\n")
        append("}\n")
    }

    private fun formatMs(ns: Long): String =
        String.format(Locale.US, "%.4f", ns.toDouble() / 1_000_000.0)

    private fun formatRatio(value: Double): String =
        String.format(Locale.US, "%.4f", value)

    private fun json(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}

object ResearchBackendBenchmark {
    val defaultCases: List<ResearchBackendBenchmarkCase> = listOf(
        ResearchBackendBenchmarkCase(
            id = "teaching_full_2_torsion",
            group = "small_exact",
            input = CurveInput(BigInteger.valueOf(-4), BigInteger.ZERO),
        ),
        ResearchBackendBenchmarkCase(
            id = "teaching_three_torsion_indicator",
            group = "small_exact",
            input = CurveInput(BigInteger.ZERO, BigInteger.ONE),
        ),
        ResearchBackendBenchmarkCase(
            id = "moderate_no_obvious_torsion",
            group = "signed_128_exact",
            input = CurveInput(BigInteger("1000003"), BigInteger("1000033")),
        ),
        ResearchBackendBenchmarkCase(
            id = "kotlin_divisor_scan_stress_b_semiprime",
            group = "both_exact_stress",
            input = CurveInput(BigInteger.ONE, BigInteger("9998000099")),
        ),
        ResearchBackendBenchmarkCase(
            id = "kotlin_three_torsion_scan_stress",
            group = "both_exact_stress",
            input = CurveInput(BigInteger("199999"), BigInteger.ONE),
        ),
        ResearchBackendBenchmarkCase(
            id = "large_smooth_a_2pow140",
            group = "smooth_bigint_exact",
            input = CurveInput(BigInteger.ONE.shiftLeft(140).negate(), BigInteger.ONE),
        ),
        ResearchBackendBenchmarkCase(
            id = "large_smooth_b_2pow130",
            group = "smooth_bigint_exact",
            input = CurveInput(BigInteger.valueOf(17), BigInteger.ONE.shiftLeft(130)),
        ),
    )

    fun run(
        cases: List<ResearchBackendBenchmarkCase> = defaultCases,
        warmupRuns: Int = 5,
        measuredRuns: Int = 30,
    ): ResearchBackendBenchmarkReport {
        val rows = cases.map { case ->
            repeat(warmupRuns) {
                ResearchDiagnosticService.evaluateKotlinOnly(case.input)
                requireNotNull(ResearchDiagnosticService.evaluateNativeOnly(case.input)) {
                    "Native C++ backend unavailable for benchmark"
                }
            }

            val kotlinTiming = measure("kotlin-bigint-fallback", measuredRuns) {
                ResearchDiagnosticService.evaluateKotlinOnly(case.input)
            }
            val nativeTiming = measure("native-cpp-adamova-v3", measuredRuns) {
                requireNotNull(ResearchDiagnosticService.evaluateNativeOnly(case.input)) {
                    "Native C++ backend unavailable for benchmark"
                }
            }
            ResearchBackendBenchmarkRow(case, kotlinTiming, nativeTiming)
        }
        return ResearchBackendBenchmarkReport(
            warmupRuns = warmupRuns,
            measuredRuns = measuredRuns,
            rows = rows,
        )
    }

    private fun measure(
        backend: String,
        runs: Int,
        block: () -> CurveDiagnosticResult,
    ): ResearchBackendTiming {
        val timings = ArrayList<Long>(runs)
        var lastResult: CurveDiagnosticResult? = null
        repeat(runs) {
            val start = System.nanoTime()
            lastResult = block()
            timings += System.nanoTime() - start
        }
        timings.sort()
        val median = if (timings.size % 2 == 0) {
            ((timings[timings.size / 2 - 1] + timings[timings.size / 2]) / 2.0).roundToLong()
        } else {
            timings[timings.size / 2]
        }
        val result = requireNotNull(lastResult)
        return ResearchBackendTiming(
            backend = backend,
            medianNs = median,
            minNs = timings.first(),
            maxNs = timings.last(),
            supported = result.localDiagnosticSupported,
            classificationCase = result.classificationCase,
        )
    }
}
