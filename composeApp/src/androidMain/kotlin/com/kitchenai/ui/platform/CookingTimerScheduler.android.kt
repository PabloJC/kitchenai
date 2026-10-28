package com.kitchenai.ui.platform

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import org.koin.core.scope.Scope

actual fun Scope.platformCookingTimerScheduler(): CookingTimerScheduler =
    AlarmCookingTimerScheduler(get<Context>().applicationContext)

/** An exact alarm when the user allows one; otherwise an inexact one, since a late timer beats a silent one. */
internal class AlarmCookingTimerScheduler(
    private val context: Context,
) : CookingTimerScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(alarm: CookingAlarm) {
        val intent =
            ring(alarm.id)
                .putExtra(CookingTimerReceiver.EXTRA_TITLE, alarm.title)
                .putExtra(CookingTimerReceiver.EXTRA_BODY, alarm.body)
                .putExtra(CookingTimerReceiver.EXTRA_CHANNEL_NAME, alarm.channelName)
                .putExtra(CookingTimerReceiver.EXTRA_STOP_LABEL, alarm.stopLabel)
        val at = alarm.fireAt.toEpochMilliseconds()
        val pending = CookingTimerReceiver.broadcast(context, alarm.id, intent)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    override fun cancel(id: String) {
        alarms.cancel(CookingTimerReceiver.broadcast(context, id, ring(id)))
        context.getSystemService(NotificationManager::class.java).cancel(id, CookingTimerReceiver.NOTIFICATION_ID)
    }

    private fun ring(id: String): Intent =
        Intent(context, CookingTimerReceiver::class.java)
            .setAction(CookingTimerReceiver.ACTION_RING)
            .putExtra(CookingTimerReceiver.EXTRA_ID, id)
}
