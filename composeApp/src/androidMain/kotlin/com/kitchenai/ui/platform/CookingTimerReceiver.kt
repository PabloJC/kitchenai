package com.kitchenai.ui.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager

/** Rings a finished cooking timer until it is stopped, and stops it from the notification's own action. */
class CookingTimerReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        if (intent.action == ACTION_STOP) {
            manager.cancel(id, NOTIFICATION_ID)
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE)
        val body = intent.getStringExtra(EXTRA_BODY)
        if (title == null || body == null || !manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(channel(intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: title))
        val stop = Intent(context, CookingTimerReceiver::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ID, id)
        val stopLabel = intent.getStringExtra(EXTRA_STOP_LABEL).orEmpty()
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(notificationSmallIcon(context))
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(launchApp(context))
                .addAction(Notification.Action.Builder(null as Icon?, stopLabel, broadcast(context, id, stop)).build())
                .setAutoCancel(true)
                .build()
        // Insistent: the sound repeats until stopped, like a kitchen timer on the counter.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(id, NOTIFICATION_ID, notification)
    }

    // Its own channel: an alarm sound at high importance, which the reminders' channel must not get.
    private fun channel(name: String): NotificationChannel =
        NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
            val attributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attributes)
        }

    private fun launchApp(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    internal companion object {
        const val ACTION_RING = "com.kitchenai.timer.RING"
        const val ACTION_STOP = "com.kitchenai.timer.STOP"
        const val EXTRA_ID = "com.kitchenai.timer.ID"
        const val EXTRA_TITLE = "com.kitchenai.timer.TITLE"
        const val EXTRA_BODY = "com.kitchenai.timer.BODY"
        const val EXTRA_CHANNEL_NAME = "com.kitchenai.timer.CHANNEL_NAME"
        const val EXTRA_STOP_LABEL = "com.kitchenai.timer.STOP_LABEL"
        const val NOTIFICATION_ID = 338
        private const val CHANNEL_ID = "cooking-timers"

        // Extras do not tell two pending intents apart; the request code does, one per timer and action.
        fun broadcast(
            context: Context,
            id: String,
            intent: Intent,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                (id + intent.action).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
