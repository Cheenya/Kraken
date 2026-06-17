package com.disser.kraken.mesh

internal object WifiDirectEndpointResolver {
    fun fromTxtRecord(
        deviceAddress: String,
        deviceName: String?,
        record: Map<String, String>,
        localFingerprint: String,
    ): WifiDirectEndpointResolveResult {
        return when (
            val binding = WifiDirectPeerBinding.bind(
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                record = record,
                localFingerprint = localFingerprint,
            )
        ) {
            is WifiDirectPeerBindingResult.Rejected ->
                WifiDirectEndpointResolveResult.Rejected(binding.reason)
            is WifiDirectPeerBindingResult.Bound ->
                WifiDirectEndpointResolveResult.Resolved(
                    peerId = binding.peerId,
                    displayName = binding.displayName,
                    binding = WifiDirectPeerBinding.discoveredUnbound(
                        fingerprint = binding.fingerprint,
                        deviceAddress = binding.deviceAddress,
                        deviceName = binding.deviceName,
                        port = binding.port,
                        source = "dns-sd-txt",
                    ),
                )
        }
    }

    fun debugHint(
        fingerprint: String,
        deviceAddress: String,
        deviceName: String?,
        port: Int,
    ): WifiDirectEndpointBinding =
        WifiDirectPeerBinding.discoveredUnbound(
            fingerprint = fingerprint,
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            port = port,
            source = "debug-directed-device-address",
            reason = "debug-directed-device-address",
        )

    fun visibleDeviceFallback(
        peer: DiscoveredPeer,
        visibleDevice: WifiDirectEndpointVisibleDevice,
        defaultPort: Int,
    ): WifiDirectEndpointBinding =
        WifiDirectPeerBinding.discoveredUnbound(
            fingerprint = peer.fingerprint,
            deviceAddress = visibleDevice.deviceAddress,
            deviceName = visibleDevice.deviceName,
            port = defaultPort,
            source = "single-visible-device-connect",
            reason = "single-visible-device-connect-endpoint-unresolved:${visibleDevice.deviceAddress}",
        )

    fun staleVisibleDeviceFallback(
        peer: DiscoveredPeer,
        cachedDeviceAddress: String,
    ): WifiDirectEndpointBinding =
        WifiDirectPeerBinding.failed(
            fingerprint = peer.fingerprint,
            state = WifiDirectEndpointBindingState.STALE,
            reason = "single-visible-device-stale-no-current-target-visible:$cachedDeviceAddress",
            deviceAddress = cachedDeviceAddress,
            source = "single-visible-device-connect",
        )

    fun groupRoute(
        peer: DiscoveredPeer,
        deviceAddress: String,
        deviceName: String?,
        port: Int,
        localIsGroupOwner: Boolean,
        groupOwnerHost: String?,
        p2pClientHost: String?,
    ): WifiDirectEndpointBinding {
        val host = if (localIsGroupOwner) p2pClientHost else groupOwnerHost
        return if (!host.isNullOrBlank()) {
            WifiDirectPeerBinding.bound(
                fingerprint = peer.fingerprint,
                deviceAddress = deviceAddress,
                deviceName = deviceName ?: peer.displayName,
                host = host,
                port = port,
                source = if (localIsGroupOwner) "fallback-p2p-client-host" else "fallback-group-owner-address",
                reason = if (localIsGroupOwner) "fallback-p2p-client-host" else "fallback-group-owner-address",
            )
        } else {
            WifiDirectPeerBinding.failed(
                fingerprint = peer.fingerprint,
                state = if (localIsGroupOwner) {
                    WifiDirectEndpointBindingState.DISCOVERED_UNBOUND
                } else {
                    WifiDirectEndpointBindingState.FAILED
                },
                reason = if (localIsGroupOwner) {
                    "group-owner-client-host-unresolved"
                } else {
                    "group-client-owner-address-missing"
                },
                deviceAddress = deviceAddress,
                deviceName = deviceName ?: peer.displayName,
                port = port,
                source = if (localIsGroupOwner) "fallback-p2p-client-host" else "fallback-group-owner-address",
            )
        }
    }

    fun connectFailed(
        peer: DiscoveredPeer,
        deviceAddress: String,
        deviceName: String?,
        port: Int,
        reason: String,
        source: String,
    ): WifiDirectEndpointBinding =
        WifiDirectPeerBinding.failed(
            fingerprint = peer.fingerprint,
            state = WifiDirectEndpointBindingState.FAILED,
            reason = reason,
            deviceAddress = deviceAddress,
            deviceName = deviceName ?: peer.displayName,
            port = port,
            source = source,
        )
}

internal data class WifiDirectEndpointVisibleDevice(
    val deviceAddress: String,
    val deviceName: String?,
)

internal sealed class WifiDirectEndpointResolveResult {
    data class Resolved(
        val peerId: String,
        val displayName: String?,
        val binding: WifiDirectEndpointBinding,
    ) : WifiDirectEndpointResolveResult()

    data class Rejected(
        val reason: String,
    ) : WifiDirectEndpointResolveResult()
}
