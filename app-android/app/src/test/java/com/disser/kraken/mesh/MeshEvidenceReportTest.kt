package com.disser.kraken.mesh

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshEvidenceReportTest {
    @Test
    fun p2pEvidenceReportsStatePrototypeLimitations() {
        val files = listOf(
            "../../reports/out/mesh_delivery_simulation.md",
            "../../reports/out/android_p2p_smoke_report.md",
            "../../reports/out/two_device_delivery_evidence.md",
            "../../reports/out/mesh_metrics_summary.json",
        )

        files.forEach { path ->
            val content = File(path).readText()
            assertTrue("Evidence file must mention prototype boundary: $path", content.contains("prototype") || content.contains("Prototype"))
            assertTrue("Evidence file must mention production boundary: $path", content.contains("production") || content.contains("Production"))
        }
    }

    @Test
    fun twoDeviceEvidenceClaimsOnlyManualPrototypeSmoke() {
        val report = File("../../reports/out/two_device_delivery_evidence.md").readText()

        assertTrue(report.contains("Status: manual two-phone LAN NSD/TCP over local Wi-Fi prototype smoke completed"))
        assertTrue(report.contains("manual two-phone Android prototype evidence"))
        assertTrue(report.contains("Repeatable capture bundle"))
        assertTrue(report.contains("automated message-send/receipt orchestration"))
        assertTrue(report.contains("still needed"))
        val claimText = report.substringBefore("Not allowed:")
        val notAllowedText = report.substringAfter("Not allowed:")
        assertFalse(claimText.contains("completed production-secure messenger"))
        assertTrue(notAllowedText.contains("completed production-secure messenger"))
    }
}
