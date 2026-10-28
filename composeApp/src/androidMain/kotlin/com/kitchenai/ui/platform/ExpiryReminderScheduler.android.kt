package com.kitchenai.ui.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kitchenai.shared.domain.usecase.reminder.PlanExpiryDigestsUseCase.Companion.HORIZON_DAYS
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.scope.Scope
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

actual fun Scope.platformExpiryReminderScheduler(): ExpiryReminderScheduler =
    WorkExpiryReminderScheduler(get<Context>().applicationContext)

/**
 * A daily [ExpiryReminderWorker] at the chosen time. WorkManager keeps it across reboots and weeks
 * without a launch, and the worker reads the pantry when it runs, so a used-up item is never
 * announced. Every pantry snapshot calls here: KEEP restores a job that went missing (a restored
 * backup carries the prefs but not WorkManager's database), and a new time, zone or UTC offset re-queues.
 */
internal class WorkExpiryReminderScheduler(
    private val context: Context,
) : ExpiryReminderScheduler {
    private val work = WorkManager.getInstance(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun schedule(
        reminders: List<ScheduledReminder>,
        hour: Int,
        minute: Int,
    ) {
        cancelLegacyAlarms()
        val zone = TimeZone.currentSystemDefault()
        // The period is a fixed 24 h: a DST change or a move shifts its wall-clock time, so they count.
        val key = "${hour * MINUTES_PER_HOUR + minute}|${zone.id}|${zone.offsetAt(Clock.System.now())}"
        val unchanged = prefs.getString(KEY_SCHEDULE, null) == key
        val request =
            PeriodicWorkRequestBuilder<ExpiryReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntil(hour, minute, zone), TimeUnit.MILLISECONDS)
                .build()
        val policy =
            if (unchanged) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        work.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        if (!unchanged) prefs.edit().putString(KEY_SCHEDULE, key).apply()
    }

    override fun cancel() {
        cancelLegacyAlarms()
        work.cancelUniqueWork(WORK_NAME)
        prefs.edit().remove(KEY_SCHEDULE).apply()
    }

    private fun millisUntil(
        hour: Int,
        minute: Int,
        zone: TimeZone,
    ): Long {
        val now = Clock.System.now()
        val today = now.toLocalDateTime(zone).date
        val time = LocalTime(hour, minute)
        val todayAt = LocalDateTime(today, time).toInstant(zone)
        val next = if (todayAt > now) todayAt else LocalDateTime(today.plus(DatePeriod(days = 1)), time).toInstant(zone)
        return (next - now).inWholeMilliseconds
    }

    // Earlier versions set one alarm per day ahead; an update must not leave them firing stale text.
    private fun cancelLegacyAlarms() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        repeat(HORIZON_DAYS) { slot ->
            val intent = Intent(context, ExpiryReminderReceiver::class.java)
            val pending =
                PendingIntent.getBroadcast(
                    context,
                    LEGACY_REQUEST_CODE + slot,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
            pending?.let(alarms::cancel)
        }
    }

    private companion object {
        const val WORK_NAME = "expiry-reminder"
        const val PREFS = "expiry-reminder"
        const val KEY_SCHEDULE = "schedule"
        const val MINUTES_PER_HOUR = 60
        const val LEGACY_REQUEST_CODE = 2760
    }
}
