package com.disser.kraken.mesh

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PrototypeMeshThreatBoundariesDocTest {
    @Test
    fun threatBoundaryDocumentsPrototypeLimits() {
        val doc = File("../../docs/prototype-mesh-threat-boundaries.md").readText()

        listOf(
            "LAN discovery не является trust",
            "Production encryption не реализован",
            "Cloud/server relay не реализован",
            "Public discovery, account/login, phone/email identity не реализуются",
            "QR/offline handshake является единственным источником `ACTIVE` relationship",
            "`proofMode = \"prototype-placeholder\"` не является цифровой подписью",
            "`INTERNET` permission используется только как Android permission для local LAN sockets",
            "`CHANGE_WIFI_MULTICAST_STATE` используется только для удержания multicast lock",
        ).forEach { required ->
            assertTrue("Missing threat boundary wording: $required", doc.contains(required))
        }
    }
}
