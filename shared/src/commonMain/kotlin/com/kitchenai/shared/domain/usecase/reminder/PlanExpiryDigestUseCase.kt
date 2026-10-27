package com.kitchenai.shared.domain.usecase.reminder

import com.kitchenai.shared.domain.model.ExpiryDigest
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.ReminderSettings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Decides the next reminder from a pantry snapshot: the next occurrence of the chosen time in
 * [zone], and what at that moment is expired or will be within a day. Null means nothing to say,
 * or reminders are off: the caller cancels whatever was scheduled. Pure, so it is rerun on every
 * snapshot rather than trusting a reminder computed from an older one.
 */
class PlanExpiryDigestUseCase {
    operator fun invoke(
        items: List<PantryItem>,
        settings: ReminderSettings,
        now: Instant,
        zone: TimeZone,
    ): ExpiryDigest? {
        if (!settings.enabled) return null
        val fireAt = nextOccurrence(settings, now, zone)
        val dated = items.mapNotNull { it.expiresAt }
        val expired = dated.count { it <= fireAt }
        val expiring = dated.count { it > fireAt && it <= fireAt + WINDOW }
        return if (expired + expiring == 0) null else ExpiryDigest(fireAt, expiring, expired)
    }

    /** Today's time if it is still ahead, tomorrow's otherwise; a DST gap moves it to the next valid instant. */
    private fun nextOccurrence(
        settings: ReminderSettings,
        now: Instant,
        zone: TimeZone,
    ): Instant {
        val today = now.toLocalDateTime(zone).date
        val time = LocalTime(settings.hour, settings.minute)
        val todayAt = LocalDateTime(today, time).toInstant(zone)
        return if (todayAt > now) todayAt else LocalDateTime(today.plus(DatePeriod(days = 1)), time).toInstant(zone)
    }

    private companion object {
        val WINDOW = 1.days
    }
}
