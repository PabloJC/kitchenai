package com.kitchenai.ui.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kitchenai.shared.domain.usecase.reminder.PlanExpiryDigestsUseCase.Companion.HORIZON_DAYS
import org.koin.core.scope.Scope

actual fun Scope.platformExpiryReminderScheduler(): ExpiryReminderScheduler =
    AlarmExpiryReminderScheduler(get<Context>().applicationContext)

/**
 * An inexact alarm: a few minutes late is fine for a daily nudge, and exact alarms need a
 * permission of their own. Alarms do not survive a reboot; the next launch plans them again.
 */
internal class AlarmExpiryReminderScheduler(
    private val context: Context,
) : ExpiryReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminders: List<ScheduledReminder>) {
        cancel()
        reminders.take(HORIZON_DAYS).forEachIndexed { slot, reminder ->
            val intent =
                Intent(context, ExpiryReminderReceiver::class.java)
                    .putExtra(ExpiryReminderReceiver.EXTRA_TITLE, reminder.title)
                    .putExtra(ExpiryReminderReceiver.EXTRA_BODY, reminder.body)
                    .putExtra(ExpiryReminderReceiver.EXTRA_CHANNEL_NAME, reminder.channelName)
            alarms.set(AlarmManager.RTC_WAKEUP, reminder.fireAt.toEpochMilliseconds(), pending(slot, intent))
        }
    }

    override fun cancel() {
        repeat(HORIZON_DAYS) { slot ->
            alarms.cancel(pending(slot, Intent(context, ExpiryReminderReceiver::class.java)))
        }
    }

    // One request code per day slot: extras do not tell two pending intents apart, the code does.
    private fun pending(
        slot: Int,
        intent: Intent,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val REQUEST_CODE = 2760
    }
}
