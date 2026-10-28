package com.kitchenai.ui.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Posts an expiry reminder that opens on the Pantry. Declined permission or a channel turned off ends quietly. */
internal object ExpiryReminderNotifier {
    const val EXTRA_OPEN_PANTRY = "com.kitchenai.reminder.OPEN_PANTRY"
    private const val CHANNEL_ID = "expiry-reminders"
    private const val NOTIFICATION_ID = 276

    /** [publicBody] is what a locked screen shows: counts, never the names in [body]. */
    fun post(
        context: Context,
        title: String,
        body: String,
        publicBody: String,
        channelName: String,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val locked =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(notificationSmallIcon(context))
                .setContentTitle(title)
                .setContentText(publicBody)
                .build()
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(notificationSmallIcon(context))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(locked)
                .setContentIntent(openPantry(context))
                .setAutoCancel(true)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** The app's own launch intent: this module cannot name the activity that lives in :androidApp. */
    private fun openPantry(context: Context): PendingIntent? =
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
}
