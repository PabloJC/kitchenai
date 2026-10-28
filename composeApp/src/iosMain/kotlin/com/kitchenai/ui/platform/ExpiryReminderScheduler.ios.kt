package com.kitchenai.ui.platform

import com.kitchenai.shared.domain.usecase.reminder.PlanExpiryDigestsUseCase.Companion.HORIZON_DAYS
import org.koin.core.scope.Scope
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock

actual fun Scope.platformExpiryReminderScheduler(): ExpiryReminderScheduler = UserNotificationsExpiryReminderScheduler()

/**
 * One identifier per day slot, so scheduling again replaces the pending reminders rather than adding to them.
 * A locked screen hides the body under iOS's default "Show previews: When unlocked".
 */
internal class UserNotificationsExpiryReminderScheduler : ExpiryReminderScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    // iOS runs no code at a set time, so the week ahead is handed over worded from the latest snapshot;
    // every snapshot while the app runs replans it. The time itself is already in each fireAt.
    override fun schedule(
        reminders: List<ScheduledReminder>,
        hour: Int,
        minute: Int,
    ) {
        cancel()
        val now = Clock.System.now()
        reminders.take(HORIZON_DAYS).forEachIndexed { slot, reminder ->
            val content =
                UNMutableNotificationContent().apply {
                    setTitle(reminder.title)
                    setBody(reminder.body)
                }
            // An interval rather than calendar components: the instant was already worked out in the right zone.
            val seconds = (reminder.fireAt - now).inWholeSeconds.coerceAtLeast(1).toDouble()
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(identifier(slot), content, trigger),
                withCompletionHandler = null,
            )
        }
    }

    override fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(List(HORIZON_DAYS, ::identifier))
    }

    private fun identifier(slot: Int): String = "$REMINDER_ID_PREFIX$slot"
}

/** Every reminder's identifier starts with this; the tap handler recognises its own by it. */
internal const val REMINDER_ID_PREFIX = "expiry-reminder-"
