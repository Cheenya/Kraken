package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiDirectConnectDiagnosticsTest {
    @Test
    fun mapsAndroidWifiP2pFailureReasonsToNames() {
        assertEquals("ERROR", WifiDirectConnectDiagnostics.failureReasonName(0))
        assertEquals("P2P_UNSUPPORTED", WifiDirectConnectDiagnostics.failureReasonName(1))
        assertEquals("BUSY", WifiDirectConnectDiagnostics.failureReasonName(2))
        assertEquals("NO_SERVICE_REQUESTS", WifiDirectConnectDiagnostics.failureReasonName(3))
        assertEquals("UNKNOWN_42", WifiDirectConnectDiagnostics.failureReasonName(42))
        assertEquals(null, WifiDirectConnectDiagnostics.failureReasonName(null))
    }

    @Test
    fun formatsConnectResultsWithAttemptIntentAndReasonName() {
        assertEquals(
            "requested:attempt=1:intent=0",
            WifiDirectConnectDiagnostics.resultLabel(
                result = "requested",
                attempt = 1,
                groupOwnerIntent = 0,
            ),
        )
        assertEquals(
            "failed:attempt=3:intent=7:reason=ERROR(0)",
            WifiDirectConnectDiagnostics.resultLabel(
                result = "failed",
                attempt = 3,
                groupOwnerIntent = 7,
                reason = 0,
            ),
        )
    }
}
