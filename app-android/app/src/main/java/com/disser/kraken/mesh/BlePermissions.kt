package com.disser.kraken.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object BlePermissions {
    fun isRuntimeSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun requiredRuntimePermissions(): Array<String> =
        if (isRuntimeSupported()) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            emptyArray()
        }

    fun hasRuntimePermissions(context: Context): Boolean =
        requiredRuntimePermissions().all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
}

