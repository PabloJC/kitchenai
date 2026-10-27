package com.kitchenai.shared.domain.usecase.reminder

import com.kitchenai.shared.domain.model.ExpiryDigest
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.model.daysUntilExpiry
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Plans the daily reminders for the next [HORIZON_DAYS] from a pantry snapshot, so they keep coming
 * with the app closed. A day gets one when something expires that day or the next; the first also
 * when something is already past its date. Empty means nothing to say, or reminders are off.
 */
class PlanExpiryDigestsUseCase {
    operator fun invoke(
        items: List<PantryItem>,
        settings: ReminderSettings,
        now: Instant,
        zone: TimeZone,
    ): List<ExpiryDigest> {
        if (!settings.enabled) return emptyList()
        val dated = items.mapNotNull { it.expiresAt }
        val time = LocalTime(settings.hour, settings.minute)
        val first = firstDay(time, now, zone)
        return (0 until HORIZON_DAYS).mapNotNull { day ->
            // A DST gap moves the time to the next valid instant.
            val fireAt = LocalDateTime(first.plus(DatePeriod(days = day)), time).toInstant(zone)
            // Calendar days, as the pantry counts them: today and tomorrow are soon, yesterday is gone.
            val days = dated.map { expiry -> daysUntilExpiry(expiry, fireAt, zone) }
            val expired = days.count { it < 0 }
            val expiring = days.count { it in 0..SOON_DAYS }
            if (expiring > 0 || (day == 0 && expired > 0)) ExpiryDigest(fireAt, expiring, expired) else null
        }
    }

    /** Today if the time is still ahead, tomorrow otherwise. */
    private fun firstDay(
        time: LocalTime,
        now: Instant,
        zone: TimeZone,
    ): LocalDate {
        val today = now.toLocalDateTime(zone).date
        return if (LocalDateTime(today, time).toInstant(zone) > now) today else today.plus(DatePeriod(days = 1))
    }

    companion object {
        /** How far ahead reminders are handed to the system; the next launch plans again from there. */
        const val HORIZON_DAYS = 7
        private const val SOON_DAYS = 1
    }
}
