package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectEndpointResolverTest {
    private val relationshipPeer = DiscoveredPeer("peer-bob", "BOB-FP", "Bob")

    @Test
    fun txtRecordCreatesDiscoveredUnboundBindingForStableFingerprint() {
        val result = WifiDirectEndpointResolver.fromTxtRecord(
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Samsung",
            record = mapOf(
                WifiDirectDnsSd.ATTR_FINGERPRINT to "BOB-FP",
                WifiDirectDnsSd.ATTR_DISPLAY_NAME to " Bob Phone ",
                WifiDirectDnsSd.ATTR_PORT to "43195",
            ),
            localFingerprint = "ALICE-FP",
        )

        assertTrue(result is WifiDirectEndpointResolveResult.Resolved)
        result as WifiDirectEndpointResolveResult.Resolved
        assertEquals("BOB-FP", result.binding.fingerprint)
        assertEquals("aa:bb:cc:dd:ee:ff", result.binding.deviceAddress)
        assertEquals(43195, result.binding.port)
        assertEquals("Bob Phone", result.displayName)
        assertEquals(WifiDirectEndpointBindingState.DISCOVERED_UNBOUND, result.binding.state)
        assertEquals(false, result.binding.endpointBound)
        assertEquals(null, result.binding.endpointKey)
    }

    @Test
    fun missingTxtFingerprintRejectsBeforeEndpointBinding() {
        val result = WifiDirectEndpointResolver.fromTxtRecord(
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Samsung",
            record = mapOf(WifiDirectDnsSd.ATTR_PORT to "43195"),
            localFingerprint = "ALICE-FP",
        )

        assertEquals(
            WifiDirectEndpointResolveResult.Rejected("missing-fingerprint:port"),
            result,
        )
    }

    @Test
    fun visibleDeviceFallbackIsDiscoveryOnlyUntilConnectBindsHost() {
        val binding = WifiDirectEndpointResolver.visibleDeviceFallback(
            peer = relationshipPeer,
            visibleDevice = WifiDirectEndpointVisibleDevice(
                deviceAddress = "11:22:33:44:55:66",
                deviceName = "Only Visible Device",
            ),
            defaultPort = 43195,
        )

        assertEquals("BOB-FP", binding.fingerprint)
        assertEquals("11:22:33:44:55:66", binding.deviceAddress)
        assertEquals("single-visible-device-connect", binding.source)
        assertEquals(WifiDirectEndpointBindingState.DISCOVERED_UNBOUND, binding.state)
        assertEquals(false, WifiDirectPeerBinding.endpointReadiness(binding).sendable)
    }

    @Test
    fun formedGroupOwnerRouteBindsClientHost() {
        val binding = WifiDirectEndpointResolver.groupRoute(
            peer = relationshipPeer,
            deviceAddress = "fallback-BOB-FP",
            deviceName = "Bob",
            port = 43195,
            localIsGroupOwner = true,
            groupOwnerHost = "192.168.49.1",
            p2pClientHost = "192.168.49.77",
        )

        assertEquals(WifiDirectEndpointBindingState.BOUND, binding.state)
        assertEquals("fallback-p2p-client-host", binding.source)
        assertEquals("192.168.49.77:43195", binding.endpointKey)
        assertEquals(true, WifiDirectPeerBinding.endpointReadiness(binding).sendable)
    }

    @Test
    fun formedGroupClientRouteBindsGroupOwnerAddress() {
        val binding = WifiDirectEndpointResolver.groupRoute(
            peer = relationshipPeer,
            deviceAddress = "fallback-BOB-FP",
            deviceName = "Bob",
            port = 43195,
            localIsGroupOwner = false,
            groupOwnerHost = "192.168.49.1",
            p2pClientHost = null,
        )

        assertEquals(WifiDirectEndpointBindingState.BOUND, binding.state)
        assertEquals("fallback-group-owner-address", binding.source)
        assertEquals("192.168.49.1:43195", binding.endpointKey)
    }

    @Test
    fun groupOwnerWithoutClientHostStaysUnboundWithStageReason() {
        val binding = WifiDirectEndpointResolver.groupRoute(
            peer = relationshipPeer,
            deviceAddress = "fallback-BOB-FP",
            deviceName = "Bob",
            port = 43195,
            localIsGroupOwner = true,
            groupOwnerHost = "192.168.49.1",
            p2pClientHost = null,
        )

        assertEquals(WifiDirectEndpointBindingState.DISCOVERED_UNBOUND, binding.state)
        assertEquals("group-owner-client-host-unresolved", binding.reason)
        assertEquals(false, WifiDirectPeerBinding.endpointReadiness(binding).endpointBound)
    }

    @Test
    fun staleAndFailedRoutesAreNotSendable() {
        val stale = WifiDirectEndpointResolver.staleVisibleDeviceFallback(
            peer = relationshipPeer,
            cachedDeviceAddress = "11:22:33:44:55:66",
        )
        val failed = WifiDirectEndpointResolver.connectFailed(
            peer = relationshipPeer,
            deviceAddress = "11:22:33:44:55:66",
            deviceName = "Bob",
            port = 43195,
            reason = "connect-failed:2",
            source = "single-visible-device-connect",
        )

        assertEquals(WifiDirectEndpointBindingState.STALE, stale.state)
        assertEquals(false, WifiDirectPeerBinding.endpointReadiness(stale).sendable)
        assertEquals(WifiDirectEndpointBindingState.FAILED, failed.state)
        assertEquals(false, WifiDirectPeerBinding.endpointReadiness(failed).sendable)
    }
}
