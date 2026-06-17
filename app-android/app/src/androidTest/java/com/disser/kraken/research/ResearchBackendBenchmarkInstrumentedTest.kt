package com.disser.kraken.research

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResearchBackendBenchmarkInstrumentedTest {
    @Test
    fun benchmarkKotlinAndNativeBackendsOnDevice() {
        val report = ResearchBackendBenchmark.run(
            warmupRuns = 5,
            measuredRuns = 30,
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir = File(context.filesDir, "research_backend_benchmark").apply { mkdirs() }
        File(outputDir, "backend_benchmark.json").writeText(report.toJson())
        File(outputDir, "backend_benchmark.md").writeText(report.toMarkdown())
        report.toMarkdown().lineSequence().forEach { line ->
            Log.i("KrakenBackendBenchmark", line)
        }

        assertTrue("benchmark should include measured rows", report.rows.isNotEmpty())
    }
}
