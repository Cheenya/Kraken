package com.disser.kraken.mesh

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.disser.kraken.MainActivity
import com.disser.kraken.R
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageService

object KrakenMessageNotifier {
    private const val MESSAGE_NOTIFICATION_TAG = "kraken-message"
    private const val MAX_CONVERSATION_NOTIFICATIONS_PER_SYNC = 3
    private const val MAX_MESSAGES_PER_CONVERSATION_NOTIFICATION = 8
    private const val NOTIFICATION_PREVIEW_LIMIT = 96
    const val EXTRA_OPEN_RELATIONSHIP_ID = "com.disser.kraken.extra.OPEN_RELATIONSHIP_ID"
    const val EXTRA_OPEN_MESSAGE_ID = "com.disser.kraken.extra.OPEN_MESSAGE_ID"
    const val EXTRA_REPLY_TEXT = "com.disser.kraken.extra.REPLY_TEXT"

    fun notifyIncomingMessages(
        context: Context,
        messages: List<LocalMessage>,
        contactNameFor: (LocalMessage) -> String?,
    ) {
        if (messages.isEmpty() || !canPostNotifications(context)) return
        KrakenNotificationChannels.ensure(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationBatches(messages).forEach { batch ->
            batch.messages.forEach { message -> manager.cancel(message.messageId.hashCode()) }
            val contactName = contactNameFor(batch.latestMessage) ?: "Kraken contact"
            val preview = batch.latestMessage.body.take(NOTIFICATION_PREVIEW_LIMIT)
            val contentText = if (batch.messages.size == 1) {
                preview
            } else {
                "${batch.messages.size} новых сообщений"
            }
            manager.notify(
                MESSAGE_NOTIFICATION_TAG,
                batch.relationshipId.hashCode(),
                Notification.Builder(context, KrakenNotificationChannels.MESSAGE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_kraken_notification)
                    .setContentTitle(contactName)
                    .setContentText(contentText)
                    .setSubText("Kraken Mesh")
                    .setTicker("$contactName: $preview")
                    .setWhen(batch.latestMessage.createdAtEpochMillis)
                    .setShowWhen(true)
                    .setNumber(batch.messages.size)
                    .setBadgeIconType(Notification.BADGE_ICON_SMALL)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setAutoCancel(false)
                    .setContentIntent(openAppIntent(context, batch.relationshipId, batch.latestMessage.messageId))
                    .applyMessagingStyle(contactName, batch.messages)
                    .addAction(replyAction(context, batch.relationshipId, batch.latestMessage.messageId))
                    .addAction(markReadAction(context, batch.relationshipId))
                    .build(),
            )
        }
    }

    fun cancelRelationshipNotification(context: Context, relationshipId: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(MESSAGE_NOTIFICATION_TAG, relationshipId.hashCode())
    }

    internal fun notificationBatches(messages: List<LocalMessage>): List<ConversationNotificationBatch> =
        messages
            .groupBy { it.relationshipId }
            .values
            .map { conversationMessages ->
                val recentMessages = MessageService
                    .sortConversationMessages(conversationMessages)
                    .takeLast(MAX_MESSAGES_PER_CONVERSATION_NOTIFICATION)
                ConversationNotificationBatch(
                    relationshipId = recentMessages.last().relationshipId,
                    messages = recentMessages,
                )
            }
            .sortedWith(
                compareBy<ConversationNotificationBatch> { it.latestMessage.createdAtEpochMillis }
                    .thenBy { it.latestMessage.messageId },
            )
            .takeLast(MAX_CONVERSATION_NOTIFICATIONS_PER_SYNC)

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun isMessageNotificationIntent(intent: Intent?): Boolean =
        !intent?.getStringExtra(EXTRA_OPEN_RELATIONSHIP_ID).isNullOrBlank()

    private fun openAppIntent(
        context: Context,
        relationshipId: String,
        messageId: String,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(20_100, relationshipId, messageId),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_RELATIONSHIP_ID, relationshipId)
                putExtra(EXTRA_OPEN_MESSAGE_ID, messageId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun replyAction(
        context: Context,
        relationshipId: String,
        latestMessageId: String,
    ): Notification.Action {
        val remoteInput = RemoteInput.Builder(EXTRA_REPLY_TEXT)
            .setLabel("Ответ")
            .build()
        val pendingIntent = PendingIntent.getService(
            context,
            requestCode(30_100, relationshipId, latestMessageId),
            Intent(context, MeshForegroundService::class.java).apply {
                action = MeshForegroundService.ACTION_REPLY
                putExtra(MeshForegroundService.EXTRA_RELATIONSHIP_ID, relationshipId)
                putExtra(MeshForegroundService.EXTRA_MESSAGE_ID, latestMessageId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_kraken_notification),
            "Ответить",
            pendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun markReadAction(context: Context, relationshipId: String): Notification.Action {
        val pendingIntent = PendingIntent.getService(
            context,
            requestCode(40_100, relationshipId),
            Intent(context, MeshForegroundService::class.java).apply {
                action = MeshForegroundService.ACTION_MARK_READ
                putExtra(MeshForegroundService.EXTRA_RELATIONSHIP_ID, relationshipId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_kraken_notification),
            "Прочитано",
            pendingIntent,
        ).build()
    }

    private fun Notification.Builder.applyMessagingStyle(
        contactName: String,
        messages: List<LocalMessage>,
    ): Notification.Builder =
        setStyle(
            messages.fold(Notification.MessagingStyle("Kraken").setConversationTitle(contactName)) { style, message ->
                style.addMessage(
                    message.body.take(NOTIFICATION_PREVIEW_LIMIT),
                    message.createdAtEpochMillis,
                    contactName,
                )
            },
        )

    private fun requestCode(base: Int, vararg parts: String): Int =
        parts.fold(base) { acc, part -> 31 * acc + part.hashCode() }

    internal data class ConversationNotificationBatch(
        val relationshipId: String,
        val messages: List<LocalMessage>,
    ) {
        val latestMessage: LocalMessage = MessageService.sortConversationMessages(messages).last()
    }
}
