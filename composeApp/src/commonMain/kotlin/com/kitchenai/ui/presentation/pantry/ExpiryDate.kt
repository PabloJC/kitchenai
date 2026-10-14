package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.Freshness
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_expired_days_ago
import com.kitchenai.ui.resources.pantry_expired_yesterday
import com.kitchenai.ui.resources.pantry_expires_in_days
import com.kitchenai.ui.resources.pantry_expires_today
import com.kitchenai.ui.resources.pantry_expires_tomorrow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/*
 * Expiry is a calendar date stored as the last instant of that day in the device's zone, so an
 * item expiring today stays "use soon" until local midnight instead of turning at UTC midnight.
 */

/** The last millisecond of [date] in [zone]: the moment it stops being that day there. */
internal fun endOfDay(
    date: LocalDate,
    zone: TimeZone,
): Instant = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - 1.milliseconds

/**
 * The calendar date a stored expiry stands for, read in [zone]. A value exactly at UTC midnight
 * was written before end-of-day storage and means its UTC date; [endOfDay] never lands there,
 * since no zone offset has a millisecond part.
 */
internal fun Instant.expiryDate(zone: TimeZone): LocalDate =
    if (toEpochMilliseconds() % MILLIS_PER_DAY == 0L) dateIn(TimeZone.UTC) else dateIn(zone)

/** The date this instant falls on in [zone], with no reading of how it was stored. */
private fun Instant.dateIn(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

private const val MILLIS_PER_DAY = 86_400_000L

/** The date picker answers with UTC midnight of the chosen day; that day is what the user meant. */
internal fun expiryFromPicker(
    millis: Long,
    zone: TimeZone,
): Instant = endOfDay(Instant.fromEpochMilliseconds(millis).dateIn(TimeZone.UTC), zone)

/** The inverse of [expiryFromPicker], to open the picker on the stored day. */
internal fun Instant.toPickerMillis(zone: TimeZone): Long =
    expiryDate(zone).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** A quick choice: [count] [unit]s from today. */
internal fun expiryIn(
    count: Int,
    unit: DateTimeUnit.DateBased,
    now: Instant,
    zone: TimeZone,
): Instant = endOfDay(now.dateIn(zone).plus(count, unit), zone)

/** Counted in calendar days, not hours: tomorrow is tomorrow whether it is 9 am or 11 pm now. */
internal fun daysUntilExpiry(
    expiresAt: Instant,
    now: Instant,
    zone: TimeZone,
): Int = now.dateIn(zone).daysUntil(expiresAt.expiryDate(zone))

/**
 * The pantry's grouping by calendar day rather than by instant, so the section always agrees
 * with [expiryLabel]: an item written before dates were stored as end of day (UTC midnight)
 * would otherwise sit under "Expired" while its label still says "Expires today".
 */
internal fun freshnessIn(daysLeft: Int?): Freshness =
    when {
        daysLeft == null -> Freshness.Unknown
        daysLeft < 0 -> Freshness.Expired
        // Today counts as one day left, as in the domain's own rounding: zero would read as expired.
        daysLeft < EXPIRING_SOON_DAYS -> Freshness.ExpiringSoon(daysLeft + 1)
        else -> Freshness.Fresh
    }

/** Today, tomorrow and the day after count as soon. A product setting, not a domain rule. */
internal const val EXPIRING_SOON_DAYS = 3

internal fun expiryLabel(daysLeft: Int): UiText =
    when (daysLeft) {
        0 -> UiText.of(Res.string.pantry_expires_today)
        1 -> UiText.of(Res.string.pantry_expires_tomorrow)
        -1 -> UiText.of(Res.string.pantry_expired_yesterday)
        in 2..Int.MAX_VALUE -> UiText.Plural(Res.plurals.pantry_expires_in_days, daysLeft)
        else -> UiText.Plural(Res.plurals.pantry_expired_days_ago, -daysLeft)
    }
