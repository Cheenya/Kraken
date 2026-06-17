package com.disser.kraken.mesh

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WifiDirectPermissionsTest {
    @Test
    fun android13PlusRequiresNearbyWifiAndFineLocation() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            WifiDirectPermissions.requiredRuntimePermissionsForSdk(Build.VERSION_CODES.TIRAMISU),
        )
        assertArrayEquals(
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            WifiDirectPermissions.requiredRuntimePermissionsForSdk(35),
        )
    }

    @Test
    fun android12AndBelowKeepsLegacyLocationRuntimePermissions() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            WifiDirectPermissions.requiredRuntimePermissionsForSdk(Build.VERSION_CODES.S_V2),
        )
    }

    @Test
    fun preRuntimePermissionDevicesRequireNoRuntimeWifiDirectPermissions() {
        assertArrayEquals(
            emptyArray<String>(),
            WifiDirectPermissions.requiredRuntimePermissionsForSdk(Build.VERSION_CODES.LOLLIPOP),
        )
    }
}
