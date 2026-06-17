package com.disser.kraken.relay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPolicyTest {
    @Test
    fun lowBatteryDisablesForwarding() {
        val state = RelayPolicyState.forMode(RelayMode.ACTIVE_COURIER)

        assertFalse(
            ForwardingAllowedEvaluator.canForwardTransit(
                state,
                RelayDeviceContext(batteryPercent = 5, isCharging = true, isWifiConnected = true),
            )
        )
    }

    @Test
    fun chargingWifiModeRequiresChargingAndWifi() {
        val state = RelayPolicyState.forMode(RelayMode.HELP_ON_CHARGING_WIFI)

        assertFalse(
            ForwardingAllowedEvaluator.canForwardTransit(
                state,
                RelayDeviceContext(batteryPercent = 80, isCharging = false, isWifiConnected = true),
            )
        )
        assertFalse(
            ForwardingAllowedEvaluator.canForwardTransit(
                state,
                RelayDeviceContext(batteryPercent = 80, isCharging = true, isWifiConnected = false),
            )
        )
        assertTrue(
            ForwardingAllowedEvaluator.canForwardTransit(
                state,
                RelayDeviceContext(batteryPercent = 80, isCharging = true, isWifiConnected = true),
            )
        )
    }

    @Test
    fun onlyMyMessagesDisablesTransitForwarding() {
        val state = RelayPolicyState.forMode(RelayMode.ONLY_MY_MESSAGES)

        assertFalse(
            ForwardingAllowedEvaluator.canForwardTransit(
                state,
                RelayDeviceContext(batteryPercent = 100, isCharging = true, isWifiConnected = true),
            )
        )
    }

    @Test
    fun researchModeAllowsBroaderLocalLimits() {
        val state = RelayPolicyState.forMode(RelayMode.RESEARCH_MODE)

        assertTrue(state.policy.maxPacketsPerWindow > RelayPolicyState.forMode(RelayMode.HELP_A_LITTLE).policy.maxPacketsPerWindow)
        assertTrue(
            ForwardingAllowedEvaluator.canForwardTransit(
                state,
                RelayDeviceContext(batteryPercent = 90, isCharging = false, isWifiConnected = false),
            )
        )
    }
}
