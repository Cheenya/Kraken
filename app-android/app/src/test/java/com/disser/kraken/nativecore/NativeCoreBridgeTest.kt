package com.disser.kraken.nativecore

import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCoreBridgeTest {
    @Test
    fun statusFallbackIsAvailableOnHostJvm() {
        val status = NativeCoreBridge.statusOrUnavailable()

        assertTrue(status.contains("Kraken native core"))
    }
}
