package com.disser.kraken.demo

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchDemoReleaseDocTest {
    @Test
    fun releaseDocBlocksFinalTagUntilManualEvidenceIsRepeatable() {
        val doc = File("../../docs/kraken-research-demo-v1-release.md").readText()

        listOf(
            "release preparation, not final tagged release",
            "Direct LAN P2P message tested",
            "manual two-device delivery",
            "evidence is repeatable",
            "demo release candidate with manual prototype evidence, not final",
            "Production encryption is not implemented",
        ).forEach { required -> assertTrue(doc.contains(required)) }
    }
}
