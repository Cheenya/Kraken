package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectDnsSdTest {
    @Test
    fun recognizesKrakenRecordsByDomainOrRequiredTxtKeys() {
        assertTrue(WifiDirectDnsSd.isKrakenService("Kraken-peer._kraken._tcp.local.", emptyMap()))
        assertTrue(WifiDirectDnsSd.isKrakenService(null, mapOf("fingerprint" to "ABCD")))
        assertTrue(WifiDirectDnsSd.isKrakenService(null, mapOf("port" to "1234")))
        assertFalse(WifiDirectDnsSd.isKrakenService("_printer._tcp.local.", mapOf("note" to "ignored")))
    }

    @Test
    fun parsesFingerprintAndValidPortFromTxtRecord() {
        val record = mapOf(
            "fingerprint" to " 3C4E D5BA 9DB8 8F9B ",
            "port" to "45678",
        )

        assertEquals("3C4E D5BA 9DB8 8F9B", WifiDirectDnsSd.fingerprint(record))
        assertEquals(45678, WifiDirectDnsSd.port(record))
    }

    @Test
    fun rejectsBlankFingerprintAndInvalidPorts() {
        assertNull(WifiDirectDnsSd.fingerprint(mapOf("fingerprint" to " ")))
        assertNull(WifiDirectDnsSd.port(mapOf("port" to "0")))
        assertNull(WifiDirectDnsSd.port(mapOf("port" to "65536")))
        assertNull(WifiDirectDnsSd.port(mapOf("port" to "not-a-port")))
    }
}
