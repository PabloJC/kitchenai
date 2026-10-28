package com.kitchenai.ui.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Posts the reminder the alarm carries. Declined permission or a channel turned off ends here quietly. */
class ExpiryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val body = intent.getStringExtra(EXTRA_BODY)
        if (title == null || body == null || !manager.areNotificationsEnabled()) return
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: title
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(notificationSmallIcon(context))
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** The app's own launch intent: this module cannot name the activity that lives in :androidApp. */
    private fun openApp(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            // SINGLE_TOP: a running app gets the tap as a new intent and keeps its state.
            launch
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_PANTRY, true)
            PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    internal companion object {
        const val EXTRA_TITLE = "com.kitchenai.reminder.TITLE"
        const val EXTRA_BODY = "com.kitchenai.reminder.BODY"
        const val EXTRA_CHANNEL_NAME = "com.kitchenai.reminder.CHANNEL_NAME"
        const val EXTRA_OPEN_PANTRY = "com.kitchenai.reminder.OPEN_PANTRY"
        private const val CHANNEL_ID = "expiry-reminders"
        private const val NOTIFICATION_ID = 276
    }
}
