package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanEndpointPayloadTest {
    @Test
    fun lanEndpointPayloadRoundTrips() {
        val payload = LanEndpointPayload(
            fingerprint = "3FBA-7C2D",
            displayName = "Alice",
            host = "192.168.1.42",
            port = 49152,
        )

        val decoded = LanEndpointPayloadCodec.decode(LanEndpointPayloadCodec.encode(payload)).getOrThrow()

        assertEquals(LanEndpointPayload.TYPE, decoded.type)
        assertEquals(1, decoded.version)
        assertEquals("3FBA-7C2D", decoded.fingerprint)
        assertEquals("Alice", decoded.displayName)
        assertEquals("192.168.1.42", decoded.host)
        assertEquals(49152, decoded.port)
    }

    @Test
    fun lanEndpointRejectsInvalidPort() {
        val raw = """
            {
              "type": "kraken_lan_endpoint",
              "version": 1,
              "fingerprint": "3FBA-7C2D",
              "host": "192.168.1.42",
              "port": 0
            }
        """.trimIndent()

        val result = LanEndpointPayloadCodec.decode(raw)

        assertTrue(result.isFailure)
    }
}
