package com.disser.kraken.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.mesh.BlePermissions
import com.disser.kraken.mesh.KrakenTransportCatalog
import com.disser.kraken.mesh.WifiDirectPermissions
import com.disser.kraken.ui.theme.KrakenError
import com.disser.kraken.ui.theme.KrakenPrimary

data class TransportPathReadiness(
    val permissionGranted: Boolean,
    val radioEnabled: Boolean,
    val serviceAvailable: Boolean,
    val transportImplemented: Boolean = true,
    val serviceRunning: Boolean = serviceAvailable,
) {
    val ready: Boolean = permissionGranted && radioEnabled && transportImplemented && serviceRunning
}

data class TransportReadinessSnapshot(
    val bluetooth: TransportPathReadiness,
    val wifi: TransportPathReadiness,
    val wifiDirect: TransportPathReadiness,
) {
    val bothReady: Boolean = bluetooth.ready && wifi.ready
    val anyReady: Boolean = bluetooth.ready || wifi.ready || wifiDirect.ready
}

@Composable
fun TransportStatusBar(
    hasLocalIdentity: Boolean,
    meshSnapshot: MeshServiceSnapshot,
    onStartMesh: () -> Unit,
    onOpenMeshStatus: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshNonce by remember { mutableIntStateOf(0) }
    var readiness by remember(refreshNonce, meshSnapshot) {
        mutableStateOf(TransportReadinessMonitor.snapshot(context, meshSnapshot))
    }
    val meshRunning = meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)
    val bluetoothActive = hasLocalIdentity && meshRunning && readiness.bluetooth.ready
    val wifiActive = hasLocalIdentity && meshRunning && readiness.wifi.ready
    val noTransportActive = !bluetoothActive && !wifiActive
    val statusText = transportChipText(
        hasLocalIdentity = hasLocalIdentity,
        meshRunning = meshRunning,
        wifiActive = wifiActive,
        bluetoothActive = bluetoothActive,
        readiness = readiness,
    )

    LaunchedEffect(hasLocalIdentity, readiness.wifi.radioEnabled, meshRunning) {
        if (hasLocalIdentity && readiness.wifi.radioEnabled && !meshRunning) {
            onStartMesh()
        }
    }

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                readiness = TransportReadinessMonitor.snapshot(context, meshSnapshot)
                refreshNonce += 1
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readiness = TransportReadinessMonitor.snapshot(context, meshSnapshot)
                refreshNonce += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMeshStatus)
                    .semantics {
                        contentDescription = "$statusText. Открыть диагностику связи рядом"
                    },
                shape = RoundedCornerShape(18.dp),
                color = if (noTransportActive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Text(
                    statusText,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    color = if (noTransportActive) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TransportPathIcon(
                    active = wifiActive,
                    activeDescription = "Wi-Fi LAN транспорт активен",
                    inactiveDescription = "Wi-Fi LAN транспорт не активен: ${readiness.wifi.reasonText("Wi‑Fi")}",
                    icon = WifiPathIcon,
                )
                TransportPathIcon(
                    active = bluetoothActive,
                    activeDescription = "Bluetooth транспорт активен",
                    inactiveDescription = "Bluetooth транспорт не активен: ${readiness.bluetooth.reasonText("Bluetooth")}",
                    icon = BluetoothPathIcon,
                )
                }
            }
        }
    }
}

private fun transportChipText(
    hasLocalIdentity: Boolean,
    meshRunning: Boolean,
    wifiActive: Boolean,
    bluetoothActive: Boolean,
    readiness: TransportReadinessSnapshot,
): String =
    when {
        !hasLocalIdentity -> "Нет профиля на устройстве"
        !meshRunning -> "Локальная связь не запущена"
        !bluetoothActive && !wifiActive ->
            "LAN недоступен · BT ${readiness.bluetooth.reasonText("Bluetooth")} · телефон не передаёт и не принимает"
        else -> listOf(
            if (wifiActive) "LAN активен" else "LAN: ${readiness.wifi.reasonText("Wi‑Fi")}",
            if (bluetoothActive) "BT активен" else "BT: ${readiness.bluetooth.reasonText("Bluetooth")}",
        ).joinToString(" · ")
    }

private fun TransportReadinessSnapshot.primaryReason(): String =
    when {
        !wifi.permissionGranted -> "нет разрешения Wi‑Fi"
        !wifi.radioEnabled -> "Wi‑Fi выключен"
        !wifi.serviceAvailable -> "LAN-служба не запущена"
        !bluetooth.serviceRunning -> "BLE-служба не запущена"
        else -> "пути недоступны"
    }

private fun TransportPathReadiness.reasonText(pathLabel: String): String =
    when {
        !permissionGranted -> "нет разрешения"
        !radioEnabled -> "$pathLabel выключен"
        !transportImplemented -> "транспорт в очереди"
        !serviceRunning -> "служба не запущена"
        else -> "mesh не запущен"
    }

@Composable
private fun TransportPathIcon(
    active: Boolean,
    activeDescription: String,
    inactiveDescription: String,
    icon: ImageVector,
) {
    val color = if (active) KrakenPrimary else KrakenError
    Box(
        modifier = Modifier
            .widthIn(min = 28.dp)
            .height(28.dp)
            .semantics { contentDescription = if (active) activeDescription else inactiveDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        if (!active) {
            Canvas(modifier = Modifier.size(25.dp)) {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.82f),
                    end = Offset(size.width * 0.82f, size.height * 0.18f),
                    strokeWidth = 2.4f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

object TransportReadinessMonitor {
    fun snapshot(context: Context, meshSnapshot: MeshServiceSnapshot? = null): TransportReadinessSnapshot {
        val appContext = context.applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val wifiPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
        val wifiEnabled = runCatching { wifiPermissionGranted && wifiManager?.isWifiEnabled == true }
            .getOrDefault(false)
        val lanReady = meshSnapshot?.transportDiagnostics?.let { diagnostics ->
            diagnostics.localPort != null && diagnostics.localAddresses.isNotEmpty()
        } == true
        val wifiDirectPermissionGranted = WifiDirectPermissions.hasRuntimePermissions(appContext)
        val wifiDirectDiagnostics = meshSnapshot?.transportDiagnostics
        val wifiDirectModeActive = wifiDirectDiagnostics?.transportModes?.contains(KrakenTransportCatalog.WIFI_DIRECT.id) == true
        val wifiDirectHasRouteEvidence = wifiDirectDiagnostics?.peerRouteEvidence
            ?.any { it.transportId == KrakenTransportCatalog.WIFI_DIRECT.id } == true
        val wifiDirectHealthyState = wifiDirectDiagnostics?.let { diagnostics ->
            diagnostics.registrationState.contains("${KrakenTransportCatalog.WIFI_DIRECT.id}:registered") ||
                diagnostics.discoveryState.contains("${KrakenTransportCatalog.WIFI_DIRECT.id}:discovering") ||
                diagnostics.discoveryState.contains("${KrakenTransportCatalog.WIFI_DIRECT.id}:connected") ||
                diagnostics.discoveryState.contains("${KrakenTransportCatalog.WIFI_DIRECT.id}:peer-found") ||
                wifiDirectHasRouteEvidence
        } == true
        val wifiDirectFailedState = wifiDirectDiagnostics?.let { diagnostics ->
            listOf(
                "permission-missing",
                "unsupported",
                "channel-unavailable",
                "adapter-off",
            ).any { marker ->
                diagnostics.registrationState.contains("${KrakenTransportCatalog.WIFI_DIRECT.id}:$marker") ||
                    diagnostics.discoveryState.contains("${KrakenTransportCatalog.WIFI_DIRECT.id}:$marker")
            }
        } == true
        val wifiDirectReady = wifiDirectModeActive && wifiDirectHealthyState && !wifiDirectFailedState
        val bluetoothSupported = BlePermissions.isRuntimeSupported()
        val bluetoothPermissionGranted = bluetoothSupported && BlePermissions.hasRuntimePermissions(appContext)
        val bluetoothEnabled = runCatching {
            bluetoothPermissionGranted && bluetoothManager?.adapter?.isEnabled == true
        }.getOrDefault(false)
        val bleReady = meshSnapshot?.transportDiagnostics?.let { diagnostics ->
            diagnostics.bleGattServerState == "ready" &&
                diagnostics.bleAdvertisingState == "started" &&
                diagnostics.bleScanningState == "started"
        } == true

        return TransportReadinessSnapshot(
            bluetooth = TransportPathReadiness(
                permissionGranted = bluetoothPermissionGranted,
                radioEnabled = bluetoothEnabled,
                serviceAvailable = bleReady,
                transportImplemented = true,
                serviceRunning = bleReady,
            ),
            wifi = TransportPathReadiness(
                permissionGranted = wifiPermissionGranted,
                radioEnabled = wifiEnabled,
                serviceAvailable = lanReady,
                transportImplemented = true,
                serviceRunning = lanReady,
            ),
            wifiDirect = TransportPathReadiness(
                permissionGranted = wifiDirectPermissionGranted,
                radioEnabled = wifiEnabled,
                serviceAvailable = wifiDirectModeActive,
                transportImplemented = true,
                serviceRunning = wifiDirectReady,
            ),
        )
    }
}

private val WifiPathIcon: ImageVector = ImageVector.Builder(
    name = "KrakenWifiPath",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(12f, 18.5f)
        lineTo(14.2f, 20.7f)
        lineTo(12f, 22.9f)
        lineTo(9.8f, 20.7f)
        close()
        moveTo(6.3f, 14.1f)
        curveTo(9.5f, 11.4f, 14.5f, 11.4f, 17.7f, 14.1f)
        lineTo(15.8f, 16f)
        curveTo(13.7f, 14.4f, 10.3f, 14.4f, 8.2f, 16f)
        close()
        moveTo(2.5f, 10.2f)
        curveTo(7.8f, 5.8f, 16.2f, 5.8f, 21.5f, 10.2f)
        lineTo(19.5f, 12.2f)
        curveTo(15.3f, 8.9f, 8.7f, 8.9f, 4.5f, 12.2f)
        close()
        moveTo(0.6f, 6.1f)
        curveTo(7.1f, 0.9f, 16.9f, 0.9f, 23.4f, 6.1f)
        lineTo(21.3f, 8.2f)
        curveTo(16f, 4.1f, 8f, 4.1f, 2.7f, 8.2f)
        close()
    }
}.build()

private val BluetoothPathIcon: ImageVector = ImageVector.Builder(
    name = "KrakenBluetoothPath",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(10.5f, 2f)
        lineTo(18.2f, 7.8f)
        lineTo(14.1f, 12f)
        lineTo(18.2f, 16.2f)
        lineTo(10.5f, 22f)
        lineTo(10.5f, 15.7f)
        lineTo(6.1f, 20.1f)
        lineTo(4.2f, 18.2f)
        lineTo(10.4f, 12f)
        lineTo(4.2f, 5.8f)
        lineTo(6.1f, 3.9f)
        lineTo(10.5f, 8.3f)
        close()
        moveTo(13.2f, 7.6f)
        lineTo(13.2f, 10.1f)
        lineTo(14.9f, 8.4f)
        close()
        moveTo(13.2f, 13.9f)
        lineTo(13.2f, 16.4f)
        lineTo(14.9f, 15.6f)
        close()
    }
}.build()
