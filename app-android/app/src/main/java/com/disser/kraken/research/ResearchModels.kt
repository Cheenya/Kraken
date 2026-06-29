package com.disser.kraken.research

import com.disser.kraken.nativecore.NativeCoreBridge
import java.math.BigInteger

data class CurveInput(
    val a: BigInteger,
    val b: BigInteger,
) {
    val shortName: String = "y^2 = x^3 + ${a}x + $b"
}

data class CurveDiagnosticResult(
    val input: CurveInput,
    val discriminantTestValue: BigInteger,
    val nonsingular: Boolean,
    val twoTorsionRootCount: Int,
    val hasThreeTorsionIndicator: Boolean,
    val classificationCase: String,
    val allowedTorsionTypes: List<String>,
    val note: String,
    val localDiagnosticSupported: Boolean = true,
    val unsupportedReasons: List<String> = emptyList(),
    val diagnosticBackend: String = "kotlin-bigint-fallback",
) {
    val diagnosticOnlyWarning: String =
        "Диагностический расчёт профиля для проверки математического контура Kraken."
}

object ResearchDiagnosticService {
    private const val MAX_EXACT_DIVISOR_SCAN = 200_000L
    private val maxExactDivisorScan = BigInteger.valueOf(MAX_EXACT_DIVISOR_SCAN)
    private val zero = BigInteger.ZERO
    private val two = BigInteger.valueOf(2)
    private val three = BigInteger.valueOf(3)
    private val four = BigInteger.valueOf(4)
    private val twentySeven = BigInteger.valueOf(27)

    fun parseCurveInput(aText: String, bText: String): Result<CurveInput> =
        runCatching {
            CurveInput(
                a = aText.trim().toBigInteger(),
                b = bText.trim().toBigInteger(),
            )
        }

    fun evaluate(input: CurveInput): CurveDiagnosticResult {
        evaluateNativeOnly(input)?.let { return it }
        return evaluateKotlinOnly(input)
    }

    fun evaluateNativeOnly(input: CurveInput): CurveDiagnosticResult? =
        NativeCoreBridge.classifyAdamovaOrNull(input.a, input.b)?.let { native ->
            val nativeSupported = native.classificationCase != "SIZE_GUARDED"
            CurveDiagnosticResult(
                input = input,
                discriminantTestValue = four * input.a.pow(3) + twentySeven * input.b.pow(2),
                nonsingular = !native.singular,
                twoTorsionRootCount = native.twoTorsionRootCount,
                hasThreeTorsionIndicator = native.hasThreeTorsionIndicator,
                classificationCase = if (native.singular) "SINGULAR" else native.classificationCase,
                allowedTorsionTypes = allowedTorsionTypes(native.classificationCase),
                note = if (nativeSupported) {
                    "Нативный C++ контур выполнил локальную проверку профиля для 128-битных и больших коэффициентов."
                } else {
                    "Нативный C++ контур вычислил дискриминант; полный перебор делителей пропущен из-за лимита проверки."
                },
                localDiagnosticSupported = nativeSupported,
                unsupportedReasons = if (nativeSupported) {
                    emptyList()
                } else {
                    listOf("нативный C++ контур пропустил полный перебор делителей для этого ввода")
                },
                diagnosticBackend = "native-cpp-adamova-v3",
            )
        }

    fun evaluateKotlinOnly(input: CurveInput): CurveDiagnosticResult {
        val discriminantTestValue = four * input.a.pow(3) + twentySeven * input.b.pow(2)
        val nonsingular = discriminantTestValue != zero
        val unsupportedReasons = mutableListOf<String>()
        val twoTorsionRootCount = if (isDivisorScanSupported(input.b)) {
            countIntegerTwoTorsionRoots(input)
        } else {
            unsupportedReasons += "поиск целых корней для точек кручения 2 порядка пропущен: |b| требует перебора делителей выше $MAX_EXACT_DIVISOR_SCAN."
            0
        }
        val hasThreeTorsionIndicator = if (isThreeTorsionScanSupported(input)) {
            hasIntegerThreeTorsionIndicator(input)
        } else {
            unsupportedReasons += "поиск индикатора точек кручения 3 порядка пропущен: |a| требует перебора делителей выше $MAX_EXACT_DIVISOR_SCAN."
            false
        }
        val localDiagnosticSupported = unsupportedReasons.isEmpty()
        val classificationCase = when {
            !nonsingular -> "SINGULAR"
            !localDiagnosticSupported -> "SIZE_GUARDED"
            else -> classifyPlaceholder(
                nonsingular = nonsingular,
                twoTorsionRootCount = twoTorsionRootCount,
                hasThreeTorsionIndicator = hasThreeTorsionIndicator,
            )
        }
        val note = if (localDiagnosticSupported) {
            "Android-контур повторяет структуру исследовательской проверки. Полное совпадение классификатора относится к C++/JNI-контуру."
        } else {
            "Локальная диагностика Android пропустила полный перебор делителей для этого ввода. Для крупных коэффициентов используйте встроенный отчёт math-core."
        }

        return CurveDiagnosticResult(
            input = input,
            discriminantTestValue = discriminantTestValue,
            nonsingular = nonsingular,
            twoTorsionRootCount = twoTorsionRootCount,
            hasThreeTorsionIndicator = hasThreeTorsionIndicator,
            classificationCase = classificationCase,
            allowedTorsionTypes = allowedTorsionTypes(classificationCase),
            note = note,
            localDiagnosticSupported = localDiagnosticSupported,
            unsupportedReasons = unsupportedReasons,
        )
    }

    private fun classifyPlaceholder(
        nonsingular: Boolean,
        twoTorsionRootCount: Int,
        hasThreeTorsionIndicator: Boolean,
    ): String =
        when {
            !nonsingular -> "SINGULAR"
            hasThreeTorsionIndicator && twoTorsionRootCount == 0 -> "A1"
            hasThreeTorsionIndicator && twoTorsionRootCount == 1 -> "A2"
            hasThreeTorsionIndicator && twoTorsionRootCount == 3 -> "A3"
            twoTorsionRootCount == 0 -> "A4"
            twoTorsionRootCount == 1 -> "A5"
            twoTorsionRootCount == 3 -> "A6"
            else -> "NA"
        }

    private fun allowedTorsionTypes(case: String): List<String> =
        when (case) {
            "A1" -> listOf("Z/3Z", "Z/9Z")
            "A2" -> listOf("Z/6Z", "Z/12Z")
            "A3" -> listOf("Z/2Z x Z/6Z")
            "A4" -> listOf("тривиальный случай", "циклический кандидат нечётного порядка")
            "A5" -> listOf("Z/2Z", "Z/4Z", "Z/8Z", "Z/10Z")
            "A6" -> listOf("Z/2Z x Z/2Z", "Z/2Z x Z/4Z", "Z/2Z x Z/8Z")
            "SIZE_GUARDED" -> emptyList()
            else -> emptyList()
        }

    private fun countIntegerTwoTorsionRoots(input: CurveInput): Int =
        twoTorsionCandidates(input)
            .count { x -> cubic(input, x) == zero }

    private fun hasIntegerThreeTorsionIndicator(input: CurveInput): Boolean =
        integerRootCandidates(input.a.pow(2).negate()).any { x ->
            val divisionValue = three * x.pow(4) + BigInteger.valueOf(6) * input.a * x.pow(2) +
                BigInteger.valueOf(12) * input.b * x - input.a.pow(2)
            divisionValue == zero && isSquare(cubic(input, x))
        }

    private fun cubic(input: CurveInput, x: BigInteger): BigInteger =
        x.pow(3) + input.a * x + input.b

    private fun twoTorsionCandidates(input: CurveInput): Set<BigInteger> {
        val candidates = integerRootCandidates(input.b).toMutableSet()
        val negativeA = input.a.negate()
        if (input.b == zero && negativeA >= zero && isSquare(negativeA)) {
            val root = integerSqrt(negativeA)
            candidates += root
            candidates += root.negate()
        }
        return candidates
    }

    private fun isThreeTorsionScanSupported(input: CurveInput): Boolean =
        input.a.abs() <= maxExactDivisorScan

    private fun isDivisorScanSupported(constant: BigInteger): Boolean {
        val absolute = constant.abs()
        if (absolute == zero) return true
        return integerSqrt(absolute) <= maxExactDivisorScan
    }

    private fun integerRootCandidates(constant: BigInteger): Set<BigInteger> {
        val absolute = constant.abs()
        if (absolute == zero) {
            return setOf(zero)
        }
        val candidates = mutableSetOf<BigInteger>()
        var divisor = BigInteger.ONE
        while (divisor * divisor <= absolute) {
            if (absolute.mod(divisor) == zero) {
                candidates += divisor
                candidates += divisor.negate()
                val paired = absolute / divisor
                candidates += paired
                candidates += paired.negate()
            }
            divisor += BigInteger.ONE
        }
        return candidates
    }

    private fun isSquare(value: BigInteger): Boolean {
        if (value < zero) return false
        val root = integerSqrt(value)
        return root * root == value
    }

    private fun integerSqrt(value: BigInteger): BigInteger {
        require(value >= zero)
        if (value < two) return value
        var estimate = BigInteger.ONE.shiftLeft((value.bitLength() + 1) / 2)
        while (true) {
            val next = estimate.add(value.divide(estimate)).shiftRight(1)
            if (next >= estimate) return estimate
            estimate = next
        }
    }
}
