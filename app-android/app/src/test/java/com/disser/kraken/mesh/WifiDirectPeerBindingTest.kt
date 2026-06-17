package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectPeerBindingTest {
    @Test
    fun bindsValidKrakenTxtRecordToPeerMetadata() {
        val result = WifiDirectPeerBinding.bind(
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Samsung",
            record = mapOf(
                WifiDirectDnsSd.ATTR_FINGERPRINT to "PEER-FP",
                WifiDirectDnsSd.ATTR_DISPLAY_NAME to " Peer Phone ",
                WifiDirectDnsSd.ATTR_PORT to "43195",
            ),
            localFingerprint = "LOCAL-FP",
        )

        assertTrue(result is WifiDirectPeerBindingResult.Bound)
        result as WifiDirectPeerBindingResult.Bound
        assertEquals("wifi-direct-aa:bb:cc:dd:ee:ff-PEER-FP", result.peerId)
        assertEquals("PEER-FP", result.fingerprint)
        assertEquals("Peer Phone", result.displayName)
        assertEquals("aa:bb:cc:dd:ee:ff", result.deviceAddress)
        assertEquals("Samsung", result.deviceName)
        assertEquals(43195, result.port)
    }

    @Test
    fun stableFingerprintSurvivesDynamicEndpointRebinding() {
        val first = WifiDirectPeerBinding.bound(
            fingerprint = "PEER-FP",
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Peer Phone",
            host = "192.168.49.1",
            port = 41001,
            source = "dns-sd-txt",
        )
        val rebound = first.copy(
            host = "192.168.49.77",
            port = 41002,
            source = "fallback-p2p-client-host",
        )

        assertEquals("PEER-FP", first.fingerprint)
        assertEquals(first.fingerprint, rebound.fingerprint)
        assertEquals("192.168.49.1:41001", first.endpointKey)
        assertEquals("192.168.49.77:41002", rebound.endpointKey)
    }

    @Test
    fun discoveredUnboundDebugHintIsNotSendableEndpoint() {
        val binding = WifiDirectPeerBinding.discoveredUnbound(
            fingerprint = "PEER-FP",
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Peer Phone",
            port = 43195,
            source = "debug-directed-device-address",
            reason = "debug-directed-device-address",
        )

        val readiness = WifiDirectPeerBinding.endpointReadiness(binding)

        assertEquals(WifiDirectEndpointBindingState.DISCOVERED_UNBOUND, binding.state)
        assertEquals("debug-directed-device-address", binding.source)
        assertEquals(null, binding.host)
        assertEquals(null, binding.endpointKey)
        assertEquals(WifiDirectEndpointBindingState.DISCOVERED_UNBOUND, readiness.state)
        assertEquals(false, readiness.endpointBound)
        assertEquals(false, readiness.sendable)
        assertEquals("debug-directed-device-address", readiness.reason)
    }

    @Test
    fun endpointReadinessReportsAllBindingStages() {
        val cases = listOf(
            null to WifiDirectEndpointReadiness(
                state = WifiDirectEndpointBindingState.UNSEEN,
                endpointBound = false,
                sendable = false,
                reason = "peer-not-discovered",
            ),
            WifiDirectPeerBinding.discoveredUnbound(
                fingerprint = "PEER-FP",
                deviceAddress = "aa:bb:cc:dd:ee:ff",
                deviceName = null,
                port = 43195,
                source = "dns-sd-txt",
            ) to WifiDirectEndpointReadiness(
                state = WifiDirectEndpointBindingState.DISCOVERED_UNBOUND,
                endpointBound = false,
                sendable = false,
                reason = "endpoint-not-bound",
            ),
            WifiDirectPeerBinding.bound(
                fingerprint = "PEER-FP",
                deviceAddress = "aa:bb:cc:dd:ee:ff",
                deviceName = null,
                host = "192.168.49.1",
                port = 43195,
                source = "resolved-group-owner",
            ) to WifiDirectEndpointReadiness(
                state = WifiDirectEndpointBindingState.BOUND,
                endpointBound = true,
                sendable = true,
                reason = null,
            ),
            WifiDirectPeerBinding.failed(
                fingerprint = "PEER-FP",
                state = WifiDirectEndpointBindingState.STALE,
                reason = "cached-host-no-longer-visible",
            ) to WifiDirectEndpointReadiness(
                state = WifiDirectEndpointBindingState.STALE,
                endpointBound = false,
                sendable = false,
                reason = "cached-host-no-longer-visible",
            ),
            WifiDirectPeerBinding.failed(
                fingerprint = "PEER-FP",
                state = WifiDirectEndpointBindingState.FAILED,
                reason = "connect-failed",
            ) to WifiDirectEndpointReadiness(
                state = WifiDirectEndpointBindingState.FAILED,
                endpointBound = false,
                sendable = false,
                reason = "connect-failed",
            ),
        )

        cases.forEach { (binding, expected) ->
            assertEquals(expected, WifiDirectPeerBinding.endpointReadiness(binding))
        }
    }

    @Test
    fun boundStateWithoutHostOrPortIsStillNotSendable() {
        val binding = WifiDirectPeerBinding.bound(
            fingerprint = "PEER-FP",
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Peer Phone",
            host = null,
            port = null,
            source = "broken-test-binding",
        )

        val readiness = WifiDirectPeerBinding.endpointReadiness(binding)

        assertEquals(WifiDirectEndpointBindingState.BOUND, readiness.state)
        assertEquals(false, readiness.endpointBound)
        assertEquals(false, readiness.sendable)
        assertEquals("bound-endpoint-missing-host-or-port", readiness.reason)
    }

    @Test
    fun rejectsMissingFingerprintWithRecordKeys() {
        val result = WifiDirectPeerBinding.bind(
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Samsung",
            record = mapOf(WifiDirectDnsSd.ATTR_PORT to "43195"),
            localFingerprint = "LOCAL-FP",
        )

        assertEquals(
            WifiDirectPeerBindingResult.Rejected("missing-fingerprint:port"),
            result,
        )
    }

    @Test
    fun rejectsSelfFingerprint() {
        val result = WifiDirectPeerBinding.bind(
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Samsung",
            record = mapOf(
                WifiDirectDnsSd.ATTR_FINGERPRINT to "LOCAL-FP",
                WifiDirectDnsSd.ATTR_PORT to "43195",
            ),
            localFingerprint = "LOCAL-FP",
        )

        assertEquals(WifiDirectPeerBindingResult.Rejected("self-fingerprint"), result)
    }

    @Test
    fun rejectsMissingOrInvalidPortWithRecordKeys() {
        val result = WifiDirectPeerBinding.bind(
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Samsung",
            record = mapOf(
                WifiDirectDnsSd.ATTR_DISPLAY_NAME to "Peer",
                WifiDirectDnsSd.ATTR_FINGERPRINT to "PEER-FP",
                WifiDirectDnsSd.ATTR_PORT to "not-a-port",
            ),
            localFingerprint = "LOCAL-FP",
        )

        assertEquals(
            WifiDirectPeerBindingResult.Rejected("missing-port:display,fingerprint,port"),
            result,
        )
    }
}
