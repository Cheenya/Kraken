package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiDirectArpTableTest {
    @Test
    fun findsClientIpForWifiDirectMacAddress() {
        val arp = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.49.59    0x1         0x2         aa:bb:cc:dd:ee:ff     *        p2p-wlan0-0
            192.168.0.1      0x1         0x2         11:22:33:44:55:66     *        wlan0
        """.trimIndent()

        assertEquals(
            "192.168.49.59",
            WifiDirectArpTable.hostForDeviceAddress(arp, "AA:BB:CC:DD:EE:FF")?.hostAddress,
        )
    }

    @Test
    fun ignoresIncompleteOrUnrelatedEntries() {
        val arp = """
            IP address       HW type     Flags       HW address            Mask     Device
            0.0.0.0          0x1         0x0         00:00:00:00:00:00     *        p2p-wlan0-0
            192.168.49.77    0x1         0x2         11:22:33:44:55:66     *        p2p-wlan0-0
        """.trimIndent()

        assertNull(WifiDirectArpTable.hostForDeviceAddress(arp, "aa:bb:cc:dd:ee:ff"))
        assertNull(WifiDirectArpTable.hostForDeviceAddress(arp, "not-a-mac"))
    }

    @Test
    fun fallsBackToSingleP2pClientAddress() {
        val arp = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.0.1      0x1         0x2         11:22:33:44:55:66     *        wlan0
            192.168.49.59    0x1         0x2         22:21:c9:0a:44:e1     *        p2p-wlan0-0
        """.trimIndent()

        assertEquals("192.168.49.59", WifiDirectArpTable.singleP2pClientHost(arp)?.hostAddress)
    }

    @Test
    fun doesNotGuessWhenMultipleP2pClientsExist() {
        val arp = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.49.59    0x1         0x2         22:21:c9:0a:44:e1     *        p2p-wlan0-0
            192.168.49.60    0x1         0x2         22:21:c9:0a:44:e2     *        p2p-wlan0-0
        """.trimIndent()

        assertNull(WifiDirectArpTable.singleP2pClientHost(arp))
    }
}
