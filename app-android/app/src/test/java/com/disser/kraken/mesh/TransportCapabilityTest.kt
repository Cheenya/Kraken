package com.disser.kraken.mesh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportCapabilityTest {
    @Test
    fun primaryTransportsAreMarkedImplemented() {
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

}
