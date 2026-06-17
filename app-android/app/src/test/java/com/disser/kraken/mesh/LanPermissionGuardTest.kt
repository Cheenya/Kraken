package com.disser.kraken.mesh

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanPermissionGuardTest {
    @Test
    fun manifestAllowsLocalLanBleAndWifiDirectWithoutBackgroundLocation() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("local LAN sockets only"))
        assertTrue(manifest.contains("android.permission.CHANGE_WIFI_MULTICAST_STATE"))
        assertTrue(manifest.contains("NSD/multicast discovery"))
        assertTrue(manifest.contains("android.permission.NEARBY_WIFI_DEVICES"))
        assertTrue(manifest.contains("Wi-Fi Direct primary transport"))
        assertTrue(manifest.contains("android.permission.ACCESS_COARSE_LOCATION"))
        assertTrue(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertTrue(manifest.contains("android:name=\"android.permission.ACCESS_COARSE_LOCATION\""))
        assertTrue(manifest.contains("android:maxSdkVersion=\"32\""))
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\" />"))
        assertTrue(manifest.contains("foreground location"))
        assertTrue(manifest.contains("no background location"))
        assertTrue(manifest.contains("android.permission.CHANGE_WIFI_STATE"))
        assertTrue(manifest.contains("android.permission.BLUETOOTH_SCAN"))
        assertTrue(manifest.contains("android:usesPermissionFlags=\"neverForLocation\""))
        assertTrue(manifest.contains("android.permission.BLUETOOTH_ADVERTISE"))
        assertTrue(manifest.contains("android.permission.BLUETOOTH_CONNECT"))
        assertTrue(manifest.contains("android.hardware.bluetooth_le"))

        listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.GET_ACCOUNTS",
        ).forEach { forbidden ->
            assertFalse("Forbidden permission present: $forbidden", manifest.contains(forbidden))
        }
    }
}
