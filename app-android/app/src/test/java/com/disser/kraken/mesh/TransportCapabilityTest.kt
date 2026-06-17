package com.disser.kraken.mesh

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportCapabilityTest {
    @Test
    fun primaryPrototypeTransportsAreMarkedImplemented() {
        val implemented = KrakenTransportCatalog.implementedTransports()

        assertTrue(implemented.any { it.id == "wifi-direct" })
        assertTrue(implemented.any { it.id == "lan-nsd-tcp" })
        assertTrue(implemented.any { it.id == "ble-gatt" })
    }

    @Test
    fun roadmapTransportsAreNotMarkedImplemented() {
        KrakenTransportCatalog.roadmapTransports().forEach { descriptor ->
            assertFalse("Roadmap transport must not be marked implemented: ${descriptor.id}", descriptor.implemented)
        }
    }

    @Test
    fun roadmapDocDoesNotExposeCloudRelay() {
        val doc = File("../../docs/multi-transport-mesh-roadmap.md").readText()

        assertTrue(doc.contains("no cloud relay"))
        assertTrue(doc.contains("UI exposes only implemented transport modes"))
        assertTrue(doc.contains("WifiP2pManager"))
        assertTrue(doc.contains("Wi-Fi Direct still requires phone route evidence"))
        assertTrue(doc.contains("LAN NSD + TCP"))
        assertTrue(doc.contains("Nearby Connections"))
    }
}
