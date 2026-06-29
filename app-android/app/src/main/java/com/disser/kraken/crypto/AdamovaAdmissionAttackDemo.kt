package com.disser.kraken.crypto

import java.math.BigInteger

enum class AdamovaAttackScenarioKind {
    SINGULAR_CURVE_PROFILE,
    TWO_TORSION_PROFILE,
    THREE_TORSION_INDICATOR_PROFILE,
    LARGE_SIZE_GUARDED_PROFILE,
    MALFORMED_PROFILE,
    DOWNGRADE_PROFILE,
    PACKET_PROFILE_MISMATCH,
}

data class AdamovaAttackScenario(
    val scenarioId: String,
    val kind: AdamovaAttackScenarioKind,
    val profile: KrakenCryptoProfile?,
    val weakOrInvalid: Boolean = true,
    val malformed: Boolean = false,
)

data class AdamovaAttackScenarioResult(
    val scenarioId: String,
    val kind: AdamovaAttackScenarioKind,
    val weakOrInvalid: Boolean,
    val noPrecheckAccepted: Boolean,
    val discriminantOnlyAccepted: Boolean,
    val adamovaDecision: AdamovaAdmissionDecision?,
    val adamovaAccepted: Boolean,
    val gateLatencyNanos: Long,
)

data class AdamovaAttackDemoMetrics(
    val profilesTotal: Int,
    val weakProfilesTotal: Int,
    val acceptedWithoutPrecheck: Int,
    val acceptedByDiscriminantOnly: Int,
    val acceptedByAdamovaGate: Int,
    val rejectedByAdamovaGate: Int,
    val needsReferenceValidation: Int,
    val sizeGuarded: Int,
    val nativeUnavailable: Int,
    val medianGateLatencyMs: Double,
    val p95GateLatencyMs: Double,
)

data class AdamovaAttackDemoReport(
    val results: List<AdamovaAttackScenarioResult>,
    val metrics: AdamovaAttackDemoMetrics,
    val safeClaim: String = SAFE_CLAIM,
    val claimBoundary: String = CLAIM_BOUNDARY,
) {
    fun toMarkdown(): String =
        buildString {
            appendLine("# Проверка допуска профилей Adamova")
            appendLine()
            appendLine("Статус: прогон из Research Mode на Android.")
            appendLine()
            appendLine("## Граница Вывода")
            appendLine()
            appendLine("- $safeClaim")
            appendLine("- $claimBoundary")
            appendLine()
            appendLine("## Метрики")
            appendLine()
            appendLine("| Метрика | Значение |")
            appendLine("| --- | ---: |")
            appendLine("| profiles_total | ${metrics.profilesTotal} |")
            appendLine("| weak_profiles_total | ${metrics.weakProfilesTotal} |")
            appendLine("| accepted_without_precheck | ${metrics.acceptedWithoutPrecheck} |")
            appendLine("| accepted_by_discriminant_only | ${metrics.acceptedByDiscriminantOnly} |")
            appendLine("| accepted_by_adamova_gate | ${metrics.acceptedByAdamovaGate} |")
            appendLine("| rejected_by_adamova_gate | ${metrics.rejectedByAdamovaGate} |")
            appendLine("| needs_reference_validation | ${metrics.needsReferenceValidation} |")
            appendLine("| size_guarded | ${metrics.sizeGuarded} |")
            appendLine("| native_unavailable | ${metrics.nativeUnavailable} |")
            appendLine("| median_gate_latency_ms | ${"%.6f".format(java.util.Locale.US, metrics.medianGateLatencyMs)} |")
            appendLine("| p95_gate_latency_ms | ${"%.6f".format(java.util.Locale.US, metrics.p95GateLatencyMs)} |")
            appendLine()
            appendLine("## Сценарии")
            appendLine()
            appendLine("| Сценарий | Тип | Без предварительной проверки | Только дискриминант | Решение Adamova | Принят Adamova | Задержка, мс |")
            appendLine("| --- | --- | ---: | ---: | --- | ---: | ---: |")
            results.forEach { result ->
                appendLine(
                    "| ${result.scenarioId} | ${result.kind} | ${result.noPrecheckAccepted} | " +
                        "${result.discriminantOnlyAccepted} | ${result.adamovaDecision ?: "none"} | " +
                        "${result.adamovaAccepted} | ${"%.6f".format(java.util.Locale.US, result.gateLatencyNanos / 1_000_000.0)} |"
                )
            }
        }

    fun toJson(): String =
        buildString {
            appendLine("{")
            appendLine("  \"safe_claim\": ${jsonString(safeClaim)},")
            appendLine("  \"claim_boundary\": ${jsonString(claimBoundary)},")
            appendLine("  \"metrics\": {")
            appendLine("    \"profiles_total\": ${metrics.profilesTotal},")
            appendLine("    \"weak_profiles_total\": ${metrics.weakProfilesTotal},")
            appendLine("    \"accepted_without_precheck\": ${metrics.acceptedWithoutPrecheck},")
            appendLine("    \"accepted_by_discriminant_only\": ${metrics.acceptedByDiscriminantOnly},")
            appendLine("    \"accepted_by_adamova_gate\": ${metrics.acceptedByAdamovaGate},")
            appendLine("    \"rejected_by_adamova_gate\": ${metrics.rejectedByAdamovaGate},")
            appendLine("    \"needs_reference_validation\": ${metrics.needsReferenceValidation},")
            appendLine("    \"size_guarded\": ${metrics.sizeGuarded},")
            appendLine("    \"native_unavailable\": ${metrics.nativeUnavailable},")
            appendLine("    \"median_gate_latency_ms\": ${metrics.medianGateLatencyMs},")
            appendLine("    \"p95_gate_latency_ms\": ${metrics.p95GateLatencyMs}")
            appendLine("  },")
            appendLine("  \"results\": [")
            results.forEachIndexed { index, result ->
                append("    {")
                append("\"scenario_id\": ${jsonString(result.scenarioId)}, ")
                append("\"kind\": ${jsonString(result.kind.name)}, ")
                append("\"weak_or_invalid\": ${result.weakOrInvalid}, ")
                append("\"no_precheck_accepted\": ${result.noPrecheckAccepted}, ")
                append("\"discriminant_only_accepted\": ${result.discriminantOnlyAccepted}, ")
                append("\"adamova_decision\": ${jsonString(result.adamovaDecision?.name)}, ")
                append("\"adamova_accepted\": ${result.adamovaAccepted}, ")
                append("\"gate_latency_nanos\": ${result.gateLatencyNanos}")
                append("}")
                appendLine(if (index == results.lastIndex) "" else ",")
            }
            appendLine("  ]")
            appendLine("}")
        }

    companion object {
        const val SAFE_CLAIM =
            "Контур допуска Adamova отклоняет или блокирует слабые экспериментальные профили до использования в сессии и сообщениях."
        const val CLAIM_BOUNDARY =
            "Граница evidence: проверка допуска Adamova отклоняет слабые экспериментальные профили до использования в сессии и сообщениях."

        private fun jsonString(value: String?): String =
            value?.replace("\\", "\\\\")
                ?.replace("\"", "\\\"")
                ?.let { "\"$it\"" }
                ?: "null"
    }
}

class AdamovaAdmissionAttackDemoRunner(
    private val gate: ProductCryptoAdmissionGate,
    private val nanoTime: () -> Long = { System.nanoTime() },
) {
    fun run(scenarios: List<AdamovaAttackScenario> = defaultScenarios()): AdamovaAttackDemoReport {
        val results = scenarios.map { scenario ->
            val noPrecheckAccepted = scenario.profile != null && !scenario.malformed
            val discriminantOnlyAccepted = discriminantOnlyAccepts(scenario)
            val start = nanoTime()
            val admission = scenario.profile?.let { gate.evaluate(it) }
            val latency = (nanoTime() - start).coerceAtLeast(0)
            AdamovaAttackScenarioResult(
                scenarioId = scenario.scenarioId,
                kind = scenario.kind,
                weakOrInvalid = scenario.weakOrInvalid,
                noPrecheckAccepted = noPrecheckAccepted,
                discriminantOnlyAccepted = discriminantOnlyAccepted,
                adamovaDecision = admission?.decision,
                adamovaAccepted = admission?.acceptedForPacketPolicy == true,
                gateLatencyNanos = latency,
            )
        }
        return AdamovaAttackDemoReport(
            results = results,
            metrics = metricsFor(results),
        )
    }

    private fun discriminantOnlyAccepts(scenario: AdamovaAttackScenario): Boolean {
        if (scenario.malformed) return false
        val profile = scenario.profile ?: return false
        if (profile.profileKind == CryptoProfileKind.STANDARD_REVIEWED_PRIMITIVES) return true
        val a = profile.curveAOrNull() ?: return false
        val b = profile.curveBOrNull() ?: return false
        return BigInteger.valueOf(4).multiply(a.pow(3))
            .add(BigInteger.valueOf(27).multiply(b.pow(2))) != BigInteger.ZERO
    }

    private fun metricsFor(results: List<AdamovaAttackScenarioResult>): AdamovaAttackDemoMetrics {
        val weak = results.filter { it.weakOrInvalid }
        val latenciesMs = results.map { it.gateLatencyNanos / 1_000_000.0 }.sorted()
        return AdamovaAttackDemoMetrics(
            profilesTotal = results.size,
            weakProfilesTotal = weak.size,
            acceptedWithoutPrecheck = weak.count { it.noPrecheckAccepted },
            acceptedByDiscriminantOnly = weak.count { it.discriminantOnlyAccepted },
            acceptedByAdamovaGate = weak.count { it.adamovaAccepted },
            rejectedByAdamovaGate = weak.count { !it.adamovaAccepted },
            needsReferenceValidation = results.count {
                it.adamovaDecision == AdamovaAdmissionDecision.REFERENCE_VALIDATION_REQUIRED
            },
            sizeGuarded = results.count { it.adamovaDecision == AdamovaAdmissionDecision.SIZE_GUARDED },
            nativeUnavailable = results.count { it.adamovaDecision == AdamovaAdmissionDecision.NATIVE_UNAVAILABLE },
            medianGateLatencyMs = percentile(latenciesMs, 0.5),
            p95GateLatencyMs = percentile(latenciesMs, 0.95),
        )
    }

    private fun percentile(sortedValues: List<Double>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = ((sortedValues.size - 1) * percentile).toInt().coerceIn(sortedValues.indices)
        return sortedValues[index]
    }

    companion object {
        fun defaultScenarios(): List<AdamovaAttackScenario> =
            listOf(
                AdamovaAttackScenario(
                    scenarioId = "singular_curve_profile",
                    kind = AdamovaAttackScenarioKind.SINGULAR_CURVE_PROFILE,
                    profile = experimentalProfile("singular-profile", a = "0", b = "0"),
                ),
                AdamovaAttackScenario(
                    scenarioId = "two_torsion_profile",
                    kind = AdamovaAttackScenarioKind.TWO_TORSION_PROFILE,
                    profile = experimentalProfile("two-torsion-profile", a = "-1", b = "0"),
                ),
                AdamovaAttackScenario(
                    scenarioId = "three_torsion_indicator_profile",
                    kind = AdamovaAttackScenarioKind.THREE_TORSION_INDICATOR_PROFILE,
                    profile = experimentalProfile("three-torsion-profile", a = "0", b = "1"),
                ),
                AdamovaAttackScenario(
                    scenarioId = "large_size_guarded_profile",
                    kind = AdamovaAttackScenarioKind.LARGE_SIZE_GUARDED_PROFILE,
                    profile = experimentalProfile(
                        id = "large-size-guarded-profile",
                        a = "340282366920938463463374607431768211507",
                        b = "340282366920938463463374607431768211297",
                    ),
                ),
                AdamovaAttackScenario(
                    scenarioId = "malformed_profile",
                    kind = AdamovaAttackScenarioKind.MALFORMED_PROFILE,
                    profile = experimentalProfile("malformed-profile", a = "not-an-integer", b = "104729"),
                    malformed = true,
                ),
                AdamovaAttackScenario(
                    scenarioId = "downgrade_to_weak_profile",
                    kind = AdamovaAttackScenarioKind.DOWNGRADE_PROFILE,
                    profile = experimentalProfile("downgrade-weak-profile", a = "-1", b = "0"),
                ),
                AdamovaAttackScenario(
                    scenarioId = "packet_profile_mismatch",
                    kind = AdamovaAttackScenarioKind.PACKET_PROFILE_MISMATCH,
                    profile = experimentalProfile("mismatched-packet-profile", a = "0", b = "1"),
                ),
            )

        private fun experimentalProfile(
            id: String,
            a: String,
            b: String,
        ): KrakenCryptoProfile =
            KrakenCryptoProfile(
                profileId = id,
                profileVersion = 1,
                profileKind = CryptoProfileKind.EXPERIMENTAL_ADAMOVA_CURVE_PROFILE,
                curveA = a,
                curveB = b,
            )
    }
}
