package com.disser.kraken.mesh

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.disser.kraken.BuildConfig
import com.disser.kraken.MainActivity
import com.disser.kraken.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MeshForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var runtime: MeshRuntime
    @Volatile
    private var loopStarted = false
    @Volatile
    private var lastMeshStatusText: String? = null
    @Volatile
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        runtime = MeshRuntime.get(applicationContext)
        KrakenNotificationChannels.ensure(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START_MESH) {
            ACTION_STOP_MESH -> {
                serviceScope.launch {
                    runtime.stop()
                    runtime.markForegroundServiceStopped()
                    foregroundStarted = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
            ACTION_SYNC_NOW -> {
                ensureForeground()
                startLoopIfNeeded()
                serviceScope.launch {
                    if (runtime.prefs.meshEnabled && runtime.snapshot().state == MeshState.OFF) {
                        runtime.startHotspotCompatible()
                    }
                    syncAndNotify()
                }
            }
            ACTION_MARK_READ -> {
                intent?.getStringExtra(EXTRA_RELATIONSHIP_ID)
                    ?.let { relationshipId ->
                        serviceScope.launch {
                            runtime.markRelationshipNotificationsRead(relationshipId)
                        }
                    }
            }
            ACTION_REPLY -> {
                val relationshipId = intent?.getStringExtra(EXTRA_RELATIONSHIP_ID)
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KrakenMessageNotifier.EXTRA_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                if (!relationshipId.isNullOrBlank() && !replyText.isNullOrBlank()) {
                    ensureForeground()
                    startLoopIfNeeded()
                    serviceScope.launch {
                        runtime.startHotspotCompatible()
                        runtime.markRelationshipNotificationsRead(relationshipId)
                        runtime.addOutgoingTextMessage(relationshipId, replyText)
                        syncAndNotify()
                    }
                }
            }
            ACTION_START_MESH -> {
                ensureForeground()
                startLoopIfNeeded()
                serviceScope.launch {
                    runtime.startHotspotCompatible()
                    syncAndNotify()
                }
            }
            ACTION_START_DEBUG_WIFI_DIRECT_ONLY -> {
                ensureForeground()
                startLoopIfNeeded()
                serviceScope.launch {
                    if (BuildConfig.DEBUG) {
                        runtime.startDebugWifiDirectOnly()
                    } else {
                        runtime.start()
                    }
                    syncAndNotify()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        runtime.markForegroundServiceStopped()
        foregroundStarted = false
        super.onDestroy()
    }

    private fun ensureForeground() {
        val status = lastMeshStatusText ?: "Запуск mesh..."
        lastMeshStatusText = status
        val notification = meshStatusNotification(status)
        if (!foregroundStarted || !isMeshStatusNotificationActive()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    MESH_STATUS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(MESH_STATUS_NOTIFICATION_ID, notification)
            }
        } else {
            getSystemService(NotificationManager::class.java)?.notify(MESH_STATUS_NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
        runtime.markForegroundServiceRunning()
    }

    private fun isMeshStatusNotificationActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return foregroundStarted
        val manager = getSystemService(NotificationManager::class.java) ?: return false
        return manager.activeNotifications.any { notification ->
            notification.id == MESH_STATUS_NOTIFICATION_ID
        }
    }

    private fun startLoopIfNeeded() {
        if (loopStarted) return
        loopStarted = true
        serviceScope.launch {
            while (true) {
                syncAndNotify()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun syncAndNotify() {
        val result = runtime.syncNow()
        updateMeshStatusNotification(result.snapshot)
    }

    private fun updateMeshStatusNotification(snapshot: MeshServiceSnapshot) {
        val status = meshStatusText(snapshot)
        val statusNotificationActive = isMeshStatusNotificationActive()
        if (status == lastMeshStatusText && statusNotificationActive) return
        if (!statusNotificationActive) {
            ensureForeground()
            return
        }
        lastMeshStatusText = status
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(MESH_STATUS_NOTIFICATION_ID, meshStatusNotification(status))
    }

    private fun meshStatusText(snapshot: MeshServiceSnapshot): String =
        when {
            snapshot.discoveredPeers.isNotEmpty() -> "Устройств рядом: ${snapshot.discoveredPeers.size}"
            snapshot.queuedPackets > 0 -> "Ждёт маршрут: ${snapshot.queuedPackets}"
            else -> "BLE/LAN поиск активен"
        }

    private fun meshStatusNotification(status: String): Notification {
        return Notification.Builder(this, KrakenNotificationChannels.MESH_STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kraken_notification)
            .setContentTitle("Kraken mesh активен")
            .setContentText(status)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setGroup(MESH_STATUS_GROUP_KEY)
            .setContentIntent(openAppIntent())
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Остановить",
                    serviceIntent(ACTION_STOP_MESH, STOP_REQUEST_CODE),
                ).build(),
            )
            .build()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, MeshForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_START_MESH = "com.disser.kraken.mesh.action.START_MESH"
        const val ACTION_STOP_MESH = "com.disser.kraken.mesh.action.STOP_MESH"
        const val ACTION_SYNC_NOW = "com.disser.kraken.mesh.action.SYNC_NOW"
        const val ACTION_REPLY = "com.disser.kraken.mesh.action.REPLY"
        const val ACTION_MARK_READ = "com.disser.kraken.mesh.action.MARK_READ"
        const val ACTION_START_DEBUG_WIFI_DIRECT_ONLY = "com.disser.kraken.mesh.action.START_DEBUG_WIFI_DIRECT_ONLY"
        const val EXTRA_RELATIONSHIP_ID = "relationshipId"
        const val EXTRA_MESSAGE_ID = "messageId"

        private const val MESH_STATUS_NOTIFICATION_ID = 10_001
        private const val OPEN_APP_REQUEST_CODE = 20_001
        private const val STOP_REQUEST_CODE = 20_002
        private const val SYNC_INTERVAL_MS = 3_000L
        private const val MESH_STATUS_GROUP_KEY = "com.disser.kraken.notification.MESH_STATUS"

        fun startMesh(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).setAction(ACTION_START_MESH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startDebugWifiDirectOnly(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
                .setAction(ACTION_START_DEBUG_WIFI_DIRECT_ONLY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopMesh(context: Context) {
            context.startService(Intent(context, MeshForegroundService::class.java).setAction(ACTION_STOP_MESH))
        }

        fun syncNow(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).setAction(ACTION_SYNC_NOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
