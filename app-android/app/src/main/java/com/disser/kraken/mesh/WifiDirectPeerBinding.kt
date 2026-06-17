package com.disser.kraken.mesh

internal object WifiDirectPeerBinding {
    fun discoveredUnbound(
        fingerprint: String,
        deviceAddress: String,
        deviceName: String?,
        port: Int,
        source: String,
        reason: String? = null,
    ): WifiDirectEndpointBinding =
        WifiDirectEndpointBinding(
            fingerprint = fingerprint,
            deviceAddress = deviceAddress,
            deviceName = deviceName?.trim()?.takeIf { it.isNotBlank() },
            host = null,
            port = port,
            state = WifiDirectEndpointBindingState.DISCOVERED_UNBOUND,
            source = source,
            reason = reason,
        )

    fun bound(
        fingerprint: String,
        deviceAddress: String,
        deviceName: String?,
        host: String?,
        port: Int?,
        source: String,
        reason: String? = null,
    ): WifiDirectEndpointBinding =
        WifiDirectEndpointBinding(
            fingerprint = fingerprint,
            deviceAddress = deviceAddress,
            deviceName = deviceName?.trim()?.takeIf { it.isNotBlank() },
            host = host?.trim()?.takeIf { it.isNotBlank() },
            port = port,
            state = WifiDirectEndpointBindingState.BOUND,
            source = source,
            reason = reason,
        )

    fun failed(
        fingerprint: String,
        state: WifiDirectEndpointBindingState,
        reason: String,
        deviceAddress: String? = null,
        deviceName: String? = null,
        port: Int? = null,
        source: String = "wifi-direct",
    ): WifiDirectEndpointBinding =
        WifiDirectEndpointBinding(
            fingerprint = fingerprint,
            deviceAddress = deviceAddress,
            deviceName = deviceName?.trim()?.takeIf { it.isNotBlank() },
            host = null,
            port = port,
            state = state,
            source = source,
            reason = reason,
        )

    fun endpointReadiness(binding: WifiDirectEndpointBinding?): WifiDirectEndpointReadiness {
        if (binding == null) {
            return WifiDirectEndpointReadiness(
                state = WifiDirectEndpointBindingState.UNSEEN,
                endpointBound = false,
                sendable = false,
                reason = "peer-not-discovered",
            )
        }
        val endpointBound = binding.endpointBound
        return WifiDirectEndpointReadiness(
            state = binding.state,
            endpointBound = endpointBound,
            sendable = endpointBound,
            reason = if (endpointBound) null else binding.reason ?: defaultReasonFor(binding.state),
        )
    }

    fun bind(
        deviceAddress: String,
        deviceName: String?,
        record: Map<String, String>,
        localFingerprint: String,
    ): WifiDirectPeerBindingResult {
        val fingerprint = WifiDirectDnsSd.fingerprint(record)
            ?: return WifiDirectPeerBindingResult.Rejected("missing-fingerprint:${recordKeys(record)}")
        if (fingerprint == localFingerprint) {
            return WifiDirectPeerBindingResult.Rejected("self-fingerprint")
        }
        val port = WifiDirectDnsSd.port(record)
            ?: return WifiDirectPeerBindingResult.Rejected("missing-port:${recordKeys(record)}")
        val displayName = record[WifiDirectDnsSd.ATTR_DISPLAY_NAME]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: deviceName
        return WifiDirectPeerBindingResult.Bound(
            peerId = "wifi-direct-$deviceAddress-$fingerprint",
            fingerprint = fingerprint,
            displayName = displayName,
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            port = port,
        )
    }

    private fun recordKeys(record: Map<String, String>): String =
        record.keys.sorted().joinToString(",").ifBlank { "none" }

    private fun defaultReasonFor(state: WifiDirectEndpointBindingState): String =
        when (state) {
            WifiDirectEndpointBindingState.UNSEEN -> "peer-not-discovered"
            WifiDirectEndpointBindingState.DISCOVERED_UNBOUND -> "endpoint-not-bound"
            WifiDirectEndpointBindingState.BOUND -> "bound-endpoint-missing-host-or-port"
            WifiDirectEndpointBindingState.STALE -> "endpoint-stale"
            WifiDirectEndpointBindingState.FAILED -> "endpoint-binding-failed"
        }
}

internal enum class WifiDirectEndpointBindingState {
    UNSEEN,
    DISCOVERED_UNBOUND,
    BOUND,
    STALE,
    FAILED,
}

internal data class WifiDirectEndpointBinding(
    val fingerprint: String,
    val deviceAddress: String?,
    val deviceName: String?,
    val host: String?,
    val port: Int?,
    val state: WifiDirectEndpointBindingState,
    val source: String,
    val reason: String? = null,
) {
    val endpointBound: Boolean
        get() = state == WifiDirectEndpointBindingState.BOUND &&
            !host.isNullOrBlank() &&
            port != null &&
            port in 1..65535

    val endpointKey: String?
        get() = if (endpointBound) "$host:$port" else null
}

internal data class WifiDirectEndpointReadiness(
    val state: WifiDirectEndpointBindingState,
    val endpointBound: Boolean,
    val sendable: Boolean,
    val reason: String?,
)

internal sealed class WifiDirectPeerBindingResult {
    data class Bound(
        val peerId: String,
        val fingerprint: String,
        val displayName: String?,
        val deviceAddress: String,
        val deviceName: String?,
        val port: Int,
    ) : WifiDirectPeerBindingResult()

    data class Rejected(
        val reason: String,
    ) : WifiDirectPeerBindingResult()
}
