package com.kitchenai.ui.platform

import org.koin.core.scope.Scope
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock

actual fun Scope.platformCookingTimerScheduler(): CookingTimerScheduler = UserNotificationsCookingTimerScheduler()

/** A local notification with sound, addressed by the timer's id so restarting a step replaces it. */
internal class UserNotificationsCookingTimerScheduler : CookingTimerScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun schedule(alarm: CookingAlarm) {
        val content =
            UNMutableNotificationContent().apply {
                setTitle(alarm.title)
                setBody(alarm.body)
                setSound(UNNotificationSound.defaultSound())
            }
        val seconds = (alarm.fireAt - Clock.System.now()).inWholeSeconds.coerceAtLeast(1).toDouble()
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(alarm.id, content, trigger),
            withCompletionHandler = null,
        )
    }

    override fun cancel(id: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
    }
}
