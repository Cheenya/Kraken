package com.disser.kraken.mesh

enum class TransportCapability {
    DISCOVERY,
    DIRECT_SEND,
    RELAY,
    LOW_BANDWIDTH,
    HIGH_BANDWIDTH,
    REQUIRES_PERMISSION,
    REQUIRES_USER_ACTION,
}

data class TransportDescriptor(
    val id: String,
    val displayName: String,
    val implemented: Boolean,
    val capabilities: Set<TransportCapability>,
    val caveat: String,
)

object KrakenTransportCatalog {
    val LAN_NSD_TCP = TransportDescriptor(
        id = "lan-nsd-tcp",
        displayName = "LAN NSD + TCP",
        implemented = true,
        capabilities = setOf(
            TransportCapability.DISCOVERY,
            TransportCapability.DIRECT_SEND,
            TransportCapability.HIGH_BANDWIDTH,
            TransportCapability.REQUIRES_PERMISSION,
        ),
        caveat = "Только локальная LAN; доверие всё равно задаётся через QR.",
    )

    val WIFI_DIRECT = TransportDescriptor(
        id = "wifi-direct",
        displayName = "Wi-Fi Direct",
        implemented = true,
        capabilities = setOf(
            TransportCapability.DISCOVERY,
            TransportCapability.DIRECT_SEND,
            TransportCapability.REQUIRES_PERMISSION,
            TransportCapability.REQUIRES_USER_ACTION,
        ),
        caveat = "Транспорт включён; для сильных утверждений нужны доказательные прогоны маршрута.",
    )

    val BLE_GATT = TransportDescriptor(
        id = "ble-gatt",
        displayName = "BLE GATT",
        implemented = true,
        capabilities = setOf(
            TransportCapability.DISCOVERY,
            TransportCapability.DIRECT_SEND,
            TransportCapability.LOW_BANDWIDTH,
            TransportCapability.REQUIRES_PERMISSION,
        ),
        caveat = "BLE GATT в foreground-режиме Android 12+; доверие всё равно задаётся через QR.",
    )

    val MANUAL_QR_PACKET_FALLBACK = TransportDescriptor(
        id = "manual-qr-packet-fallback",
        displayName = "Передача пакета через QR",
        implemented = false,
        capabilities = setOf(
            TransportCapability.REQUIRES_USER_ACTION,
            TransportCapability.LOW_BANDWIDTH,
        ),
        caveat = "Идея для резервной передачи без радиоканала P2P.",
    )

    fun implementedTransports(): List<TransportDescriptor> =
        listOf(WIFI_DIRECT, LAN_NSD_TCP, BLE_GATT)

    fun roadmapTransports(): List<TransportDescriptor> =
        listOf(MANUAL_QR_PACKET_FALLBACK)
}
