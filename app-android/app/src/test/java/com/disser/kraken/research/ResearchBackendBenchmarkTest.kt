package com.disser.kraken.research

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchBackendBenchmarkTest {
    @Test
    fun reportComputesNativeSpeedupFromSummedMedians() {
        val report = fakeReport(
            kotlinNs = 10_000,
            nativeNs = 2_500,
            supported = true,
        )

        assertEquals(4.0, report.totalSpeedup, 0.0001)
        assertEquals(4.0, report.comparableSpeedup, 0.0001)
        assertEquals(1, report.comparableRows.size)
    }

    @Test
    fun unsupportedRowsAreExcludedFromComparableSpeedup() {
        val supported = fakeRow("supported", 8_000, 2_000, supported = true)
        val guarded = fakeRow("guarded", 100_000, 1_000, supported = false)
        val report = ResearchBackendBenchmarkReport(
            warmupRuns = 1,
            measuredRuns = 2,
            rows = listOf(supported, guarded),
        )

        assertEquals(2, report.rows.size)
        assertEquals(1, report.comparableRows.size)
        assertEquals(4.0, report.comparableSpeedup, 0.0001)
    }

    @Test
    fun reportSerializationKeepsNeutralCryptoScope() {
        val report = fakeReport(
            kotlinNs = 10_000,
            nativeNs = 5_000,
            supported = true,
        )

        val markdown = report.toMarkdown()
        val json = report.toJson()

        assertTrue(markdown.contains("Бенчмарк показывает время работы"))
        assertTrue(json.contains("Бенчмарк показывает время работы"))
        assertFalse(markdown.contains("готовый криптографический контур", ignoreCase = true))
        assertFalse(json.contains("готовый криптографический контур", ignoreCase = true))
    }

    private fun fakeReport(
        kotlinNs: Long,
        nativeNs: Long,
        supported: Boolean,
    ): ResearchBackendBenchmarkReport =
        ResearchBackendBenchmarkReport(
            warmupRuns = 1,
            measuredRuns = 3,
            rows = listOf(fakeRow("case", kotlinNs, nativeNs, supported)),
        )

    private fun fakeRow(
        id: String,
        kotlinNs: Long,
        nativeNs: Long,
        supported: Boolean,
    ): ResearchBackendBenchmarkRow =
        ResearchBackendBenchmarkRow(
            case = ResearchBackendBenchmarkCase(
                id = id,
                group = "test",
                input = CurveInput(BigInteger.ONE, BigInteger.TWO),
            ),
            kotlinTiming = ResearchBackendTiming(
                backend = "kotlin-bigint-fallback",
                medianNs = kotlinNs,
                minNs = kotlinNs,
                maxNs = kotlinNs,
                supported = supported,
                classificationCase = if (supported) "A4" else "SIZE_GUARDED",
            ),
            nativeTiming = ResearchBackendTiming(
                backend = "native-cpp-adamova-v3",
                medianNs = nativeNs,
                minNs = nativeNs,
                maxNs = nativeNs,
                supported = supported,
                classificationCase = if (supported) "A4" else "SIZE_GUARDED",
            ),
        )
}
