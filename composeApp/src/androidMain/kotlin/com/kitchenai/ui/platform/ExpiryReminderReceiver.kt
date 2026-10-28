package com.kitchenai.ui.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Posts a reminder an alarm from an earlier version still carries; current versions schedule
 * [ExpiryReminderWorker] instead. Kept so an alarm already pending on an update is not lost.
 */
class ExpiryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: title
        ExpiryReminderNotifier.post(context, title, body, publicBody = body, channelName = channelName)
    }

    internal companion object {
        const val EXTRA_TITLE = "com.kitchenai.reminder.TITLE"
        const val EXTRA_BODY = "com.kitchenai.reminder.BODY"
        const val EXTRA_CHANNEL_NAME = "com.kitchenai.reminder.CHANNEL_NAME"
    }
}
