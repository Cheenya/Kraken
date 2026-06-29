package com.disser.kraken.mesh

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PrototypeMeshThreatBoundariesDocTest {
    @Test
    fun threatBoundaryDocumentsMeshScope() {
        val doc = File("../../docs/prototype-mesh-threat-boundaries.md").readText()

        listOf(
            "LAN discovery не является trust",
            "Cloud/server relay не входит в этот локальный транспортный слой",
            "Account/login/phone/email identity находятся за пределами локальной модели идентичности Kraken",
            "QR/offline handshake является единственным источником `ACTIVE` relationship",
            "`proofMode = \"prototype-placeholder\"` является служебным маркером старого packet path",
            "`INTERNET` permission используется только как Android permission для local LAN sockets",
            "`CHANGE_WIFI_MULTICAST_STATE` используется только для удержания multicast lock",
            "защищённый message payload path использует `ENCRYPTED_MESSAGE_JSON`",
        ).forEach { required ->
            assertTrue("Missing threat boundary wording: $required", doc.contains(required))
        }
    }
}
