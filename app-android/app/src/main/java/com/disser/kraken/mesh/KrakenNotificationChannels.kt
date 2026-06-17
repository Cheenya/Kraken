package com.disser.kraken.mesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object KrakenNotificationChannels {
    const val MESH_STATUS_CHANNEL_ID = "mesh_status_v2"
    const val MESSAGE_CHANNEL_ID = "messages_v3"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                MESH_STATUS_CHANNEL_ID,
                "Kraken mesh",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Тихое постоянное уведомление фоновой связи рядом."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Сообщения",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Локальные уведомления о входящих mesh-сообщениях."
                enableVibration(true)
                setShowBadge(true)
            },
        )
    }
}
