package com.disser.kraken.relay

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RelayMode {
    ONLY_MY_MESSAGES,
    HELP_A_LITTLE,
    HELP_ON_CHARGING_WIFI,
    ACTIVE_COURIER,
    RESEARCH_MODE,
}

@Serializable
enum class RelayRuntimeState {
    OFFLINE,
    IDLE,
    PASSIVE_SCAN,
    ACTIVE_HANDSHAKE,
    ONLINE_SESSION,
    TRANSIT_FORWARDING,
    LOW_POWER,
}

@Serializable
data class RelayBatteryPolicy(
    @SerialName("scan_window_duration_seconds")
    val scanWindowDurationSeconds: Int,
    @SerialName("scan_interval_seconds")
    val scanIntervalSeconds: Int,
    @SerialName("max_packets_per_window")
    val maxPacketsPerWindow: Int,
    @SerialName("max_bytes_per_window")
    val maxBytesPerWindow: Int,
    @SerialName("max_forwarding_time_seconds")
    val maxForwardingTimeSeconds: Int,
    @SerialName("battery_threshold_percent")
    val batteryThresholdPercent: Int,
    @SerialName("wifi_only")
    val wifiOnly: Boolean,
    @SerialName("charging_only")
    val chargingOnly: Boolean,
)

@Serializable
data class RelayPolicyState(
    val mode: RelayMode,
    val runtimeState: RelayRuntimeState,
    val policy: RelayBatteryPolicy,
) {
    companion object {
        fun default(): RelayPolicyState = forMode(RelayMode.ONLY_MY_MESSAGES)

        fun forMode(mode: RelayMode): RelayPolicyState =
            RelayPolicyState(
                mode = mode,
                runtimeState = RelayRuntimeState.IDLE,
                policy = when (mode) {
                    RelayMode.ONLY_MY_MESSAGES -> RelayBatteryPolicy(
                        scanWindowDurationSeconds = 0,
                        scanIntervalSeconds = 0,
                        maxPacketsPerWindow = 0,
                        maxBytesPerWindow = 0,
                        maxForwardingTimeSeconds = 0,
                        batteryThresholdPercent = 100,
                        wifiOnly = true,
                        chargingOnly = true,
                    )
                    RelayMode.HELP_A_LITTLE -> RelayBatteryPolicy(
                        scanWindowDurationSeconds = 15,
                        scanIntervalSeconds = 900,
                        maxPacketsPerWindow = 4,
                        maxBytesPerWindow = 128 * 1024,
                        maxForwardingTimeSeconds = 20,
                        batteryThresholdPercent = 35,
                        wifiOnly = false,
                        chargingOnly = false,
                    )
                    RelayMode.HELP_ON_CHARGING_WIFI -> RelayBatteryPolicy(
                        scanWindowDurationSeconds = 60,
                        scanIntervalSeconds = 600,
                        maxPacketsPerWindow = 16,
                        maxBytesPerWindow = 512 * 1024,
                        maxForwardingTimeSeconds = 90,
                        batteryThresholdPercent = 25,
                        wifiOnly = true,
                        chargingOnly = true,
                    )
                    RelayMode.ACTIVE_COURIER -> RelayBatteryPolicy(
                        scanWindowDurationSeconds = 120,
                        scanIntervalSeconds = 300,
                        maxPacketsPerWindow = 64,
                        maxBytesPerWindow = 2 * 1024 * 1024,
                        maxForwardingTimeSeconds = 180,
                        batteryThresholdPercent = 20,
                        wifiOnly = false,
                        chargingOnly = false,
                    )
                    RelayMode.RESEARCH_MODE -> RelayBatteryPolicy(
                        scanWindowDurationSeconds = 180,
                        scanIntervalSeconds = 180,
                        maxPacketsPerWindow = 128,
                        maxBytesPerWindow = 4 * 1024 * 1024,
                        maxForwardingTimeSeconds = 300,
                        batteryThresholdPercent = 15,
                        wifiOnly = false,
                        chargingOnly = false,
                    )
                },
            )
    }
}

data class RelayDeviceContext(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isWifiConnected: Boolean,
)

object ForwardingAllowedEvaluator {
    fun canForwardTransit(
        state: RelayPolicyState,
        context: RelayDeviceContext,
    ): Boolean {
        if (state.mode == RelayMode.ONLY_MY_MESSAGES) return false
        if (context.batteryPercent < state.policy.batteryThresholdPercent) return false
        if (state.policy.chargingOnly && !context.isCharging) return false
        if (state.policy.wifiOnly && !context.isWifiConnected) return false
        return state.policy.maxPacketsPerWindow > 0 && state.policy.maxBytesPerWindow > 0
    }

    fun runtimeStateFor(
        state: RelayPolicyState,
        context: RelayDeviceContext,
    ): RelayRuntimeState =
        if (canForwardTransit(state, context)) RelayRuntimeState.TRANSIT_FORWARDING else RelayRuntimeState.LOW_POWER
}
