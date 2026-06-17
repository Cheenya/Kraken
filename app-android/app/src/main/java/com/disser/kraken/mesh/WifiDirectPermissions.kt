package com.disser.kraken.mesh

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class WifiDirectPermissionDiagnostics(
    val nearbyWifiDevicesRequired: Boolean,
    val nearbyWifiDevicesDeclared: Boolean,
    val nearbyWifiDevicesGranted: Boolean,
    val fineLocationRequired: Boolean,
    val fineLocationDeclared: Boolean,
    val fineLocationGranted: Boolean,
    val fineLocationAppOpMode: String?,
) {
    val warning: String?
        get() = when {
            fineLocationRequired && !fineLocationDeclared -> "wifi-direct-fine-location-not-declared"
            fineLocationRequired && !fineLocationGranted -> "wifi-direct-fine-location-missing-modern-android"
            nearbyWifiDevicesRequired && !nearbyWifiDevicesDeclared -> "wifi-direct-nearby-wifi-devices-not-declared"
            nearbyWifiDevicesRequired && !nearbyWifiDevicesGranted -> "wifi-direct-nearby-wifi-devices-missing"
            else -> null
        }
}

object WifiDirectPermissions {
    fun isRuntimeSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    fun requiredRuntimePermissions(): Array<String> =
        requiredRuntimePermissionsForSdk(Build.VERSION.SDK_INT)

    internal fun requiredRuntimePermissionsForSdk(sdkInt: Int): Array<String> =
        when {
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            }
            sdkInt >= Build.VERSION_CODES.M -> {
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            }
            else -> {
                emptyArray()
            }
        }

    fun hasRuntimePermissions(context: Context): Boolean =
        requiredRuntimePermissions().all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

    fun permissionDiagnostics(context: Context): WifiDirectPermissionDiagnostics {
        val required = requiredRuntimePermissions().toSet()
        return WifiDirectPermissionDiagnostics(
            nearbyWifiDevicesRequired = Manifest.permission.NEARBY_WIFI_DEVICES in required,
            nearbyWifiDevicesDeclared = context.isPermissionDeclared(Manifest.permission.NEARBY_WIFI_DEVICES),
            nearbyWifiDevicesGranted = context.isPermissionGranted(Manifest.permission.NEARBY_WIFI_DEVICES),
            fineLocationRequired = Manifest.permission.ACCESS_FINE_LOCATION in required,
            fineLocationDeclared = context.isPermissionDeclared(Manifest.permission.ACCESS_FINE_LOCATION),
            fineLocationGranted = context.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION),
            fineLocationAppOpMode = context.fineLocationAppOpMode(),
        )
    }

    private fun Context.isPermissionGranted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun Context.isPermissionDeclared(permission: String): Boolean =
        packageManager
            .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(permission)
            ?: false

    private fun Context.fineLocationAppOpMode(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        val appOps = getSystemService(AppOpsManager::class.java) ?: return null
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_FINE_LOCATION,
            android.os.Process.myUid(),
            packageName,
        )
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> "allowed"
            AppOpsManager.MODE_DEFAULT -> "default"
            AppOpsManager.MODE_ERRORED -> "errored"
            AppOpsManager.MODE_FOREGROUND -> "foreground"
            AppOpsManager.MODE_IGNORED -> "ignored"
            else -> "mode-$mode"
        }
    }
}
