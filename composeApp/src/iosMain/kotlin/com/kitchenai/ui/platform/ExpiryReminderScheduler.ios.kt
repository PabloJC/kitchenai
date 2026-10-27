package com.kitchenai.ui.platform

import org.koin.core.scope.Scope
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock

actual fun Scope.platformExpiryReminderScheduler(): ExpiryReminderScheduler = UserNotificationsExpiryReminderScheduler()

/** One identifier, so scheduling again replaces the pending reminder rather than adding a second. */
internal class UserNotificationsExpiryReminderScheduler : ExpiryReminderScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun schedule(reminder: ScheduledReminder) {
        val content =
            UNMutableNotificationContent().apply {
                title = reminder.title
                body = reminder.body
            }
        // An interval rather than calendar components: the instant was already worked out in the right zone.
        val seconds = (reminder.fireAt - Clock.System.now()).inWholeSeconds.coerceAtLeast(1).toDouble()
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(IDENTIFIER, content, trigger),
            withCompletionHandler = null,
        )
    }

    override fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))
    }

    private companion object {
        const val IDENTIFIER = "expiry-reminder"
    }
}
