package com.disser.kraken.mesh

data class MeshTransportSelection(
    val wifiDirect: Boolean = true,
    val bleGatt: Boolean = true,
    val lanNsdTcp: Boolean = true,
) {
    val enabledModeIds: List<String>
        get() = buildList {
            if (wifiDirect) add(KrakenTransportCatalog.WIFI_DIRECT.id)
            if (bleGatt) add(KrakenTransportCatalog.BLE_GATT.id)
            if (lanNsdTcp) add(KrakenTransportCatalog.LAN_NSD_TCP.id)
        }

    companion object {
        const val PROFILE_AUTO = "auto"
        const val PROFILE_HOTSPOT_COMPATIBLE = "hotspot-compatible"
        const val PROFILE_WIFI_DIRECT_ONLY = "wifi-direct-only"
        const val PROFILE_LAN_ONLY = "lan-only"

        val AUTO = MeshTransportSelection(
            wifiDirect = true,
            bleGatt = true,
            lanNsdTcp = true,
        )

        val HOTSPOT_COMPATIBLE = MeshTransportSelection(
            wifiDirect = false,
            bleGatt = true,
            lanNsdTcp = true,
        )

        val WIFI_DIRECT_ONLY = MeshTransportSelection(
            wifiDirect = true,
            bleGatt = false,
            lanNsdTcp = false,
        )

        val LAN_ONLY = MeshTransportSelection(
            wifiDirect = false,
            bleGatt = false,
            lanNsdTcp = true,
        )

        fun fromWifiDirectEnabled(wifiDirectEnabled: Boolean): MeshTransportSelection =
            AUTO.copy(
                wifiDirect = wifiDirectEnabled,
            )

        fun fromProfileId(profileId: String?): MeshTransportSelection =
            when (profileId) {
                PROFILE_AUTO -> AUTO
                PROFILE_WIFI_DIRECT_ONLY -> WIFI_DIRECT_ONLY
                PROFILE_LAN_ONLY -> LAN_ONLY
                PROFILE_HOTSPOT_COMPATIBLE, null, "" -> HOTSPOT_COMPATIBLE
                else -> HOTSPOT_COMPATIBLE
            }
    }
}
