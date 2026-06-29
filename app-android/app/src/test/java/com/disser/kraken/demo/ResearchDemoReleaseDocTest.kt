package com.disser.kraken.demo

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchDemoReleaseDocTest {
    @Test
    fun releaseDocBlocksFinalTagUntilManualEvidenceIsRepeatable() {
        val doc = File("../../docs/kraken-research-demo-v1-release.md").readText()

        listOf(
            "чеклист кандидата релиза для debug-сборки исследовательского демо",
            "Прямое LAN P2P-сообщение проверено",
            "повторяемым ручным evidence на двух устройствах",
            "evidence повторяем как capture bundle",
            "demo release candidate",
            "Защищённый путь данных и debug compatibility path разделены",
        ).forEach { required -> assertTrue(doc.contains(required)) }
    }
}
