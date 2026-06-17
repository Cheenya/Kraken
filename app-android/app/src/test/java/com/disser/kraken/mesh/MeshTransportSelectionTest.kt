package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class MeshTransportSelectionTest {
    @Test
    fun autoUserSelectionKeepsWifiDirectBleAndLanTogether() {
        assertEquals(
            listOf(
                KrakenTransportCatalog.WIFI_DIRECT.id,
                KrakenTransportCatalog.BLE_GATT.id,
                KrakenTransportCatalog.LAN_NSD_TCP.id,
            ),
            MeshTransportSelection.AUTO.enabledModeIds,
        )
    }

    @Test
    fun hotspotCompatibleSelectionKeepsLanAndBleWithoutWifiDirect() {
        assertEquals(
            listOf(
                KrakenTransportCatalog.BLE_GATT.id,
                KrakenTransportCatalog.LAN_NSD_TCP.id,
            ),
            MeshTransportSelection.HOTSPOT_COMPATIBLE.enabledModeIds,
        )
    }

    @Test
    fun unknownProfileFallsBackToHotspotCompatibleSelection() {
        assertEquals(
            MeshTransportSelection.HOTSPOT_COMPATIBLE.enabledModeIds,
            MeshTransportSelection.fromProfileId("old-or-unknown").enabledModeIds,
        )
    }

    @Test
    fun legacyWifiDirectToggleSelectionIsNotTheMainUserMode() {
        assertEquals(
            listOf(
                KrakenTransportCatalog.BLE_GATT.id,
                KrakenTransportCatalog.LAN_NSD_TCP.id,
            ),
            MeshTransportSelection.fromWifiDirectEnabled(false).enabledModeIds,
        )
    }

    @Test
    fun debugWifiDirectOnlySelectionDisablesFallbackTransports() {
        assertEquals(
            listOf(KrakenTransportCatalog.WIFI_DIRECT.id),
            MeshTransportSelection.WIFI_DIRECT_ONLY.enabledModeIds,
        )
    }

    @Test
    fun debugLanOnlySelectionDisablesBleAndWifiDirectFallbackTransports() {
        assertEquals(
            listOf(KrakenTransportCatalog.LAN_NSD_TCP.id),
            MeshTransportSelection.LAN_ONLY.enabledModeIds,
        )
    }
}
