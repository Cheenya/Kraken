package com.disser.kraken.mesh

import java.net.InetAddress

internal object WifiDirectArpTable {
    fun hostForDeviceAddress(
        arpTable: String,
        deviceAddress: String,
    ): InetAddress? {
        val expected = normalizeMac(deviceAddress) ?: return null
        return arpTable
            .lineSequence()
            .drop(1)
            .mapNotNull { line -> parseEntry(line) }
            .firstOrNull { entry ->
                entry.hardwareAddress == expected &&
                    entry.ipAddress != "0.0.0.0" &&
                    entry.hardwareAddress != EMPTY_MAC
            }
            ?.ipAddress
            ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
    }

    fun singleP2pClientHost(arpTable: String): InetAddress? {
        val clients = arpTable
            .lineSequence()
            .drop(1)
            .mapNotNull { line -> parseEntry(line) }
            .filter { entry ->
                entry.interfaceName.startsWith("p2p") &&
                    entry.ipAddress != "0.0.0.0" &&
                    entry.hardwareAddress != EMPTY_MAC
            }
            .map { entry -> entry.ipAddress }
            .distinct()
            .toList()
        if (clients.size != 1) return null
        return runCatching { InetAddress.getByName(clients.single()) }.getOrNull()
    }

    private fun parseEntry(line: String): Entry? {
        val columns = line.trim().split(Regex("\\s+"))
        if (columns.size < 6) return null
        val ip = columns[0].takeIf { it.contains('.') } ?: return null
        val hardwareAddress = normalizeMac(columns[3]) ?: return null
        return Entry(
            ipAddress = ip,
            hardwareAddress = hardwareAddress,
            interfaceName = columns[5],
        )
    }

    private fun normalizeMac(value: String): String? {
        val compact = value
            .trim()
            .lowercase()
            .replace("-", ":")
        val parts = compact.split(':')
        if (parts.size != 6 || parts.any { it.length !in 1..2 }) return null
        return parts.joinToString(":") { it.padStart(2, '0') }
    }

    private data class Entry(
        val ipAddress: String,
        val hardwareAddress: String,
        val interfaceName: String,
    )

    private const val EMPTY_MAC = "00:00:00:00:00:00"
}
