package com.kitchenai.shared.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * Expiry is a calendar day, judged in the device's zone: an item expiring today stays usable until
 * local midnight wherever the device is. The pantry screen, the matcher and the agent all read it here.
 */

/**
 * The calendar date a stored expiry stands for, read in [zone]. A value exactly at UTC midnight
 * was written before end-of-day storage and means its UTC date; end of day in any zone never lands
 * there, since no zone offset has a millisecond part.
 */
fun Instant.expiryDate(zone: TimeZone): LocalDate =
    if (toEpochMilliseconds() % MILLIS_PER_DAY == 0L) dateIn(TimeZone.UTC) else dateIn(zone)

/** Counted in calendar days, not hours: tomorrow is tomorrow whether it is 9 am or 11 pm now. */
fun daysUntilExpiry(
    expiresAt: Instant,
    now: Instant,
    zone: TimeZone,
): Int = now.dateIn(zone).daysUntil(expiresAt.expiryDate(zone))

/** Expired before today; soon for today and the next [soonDays] minus one days. */
fun freshnessIn(
    daysLeft: Int?,
    soonDays: Int,
): Freshness =
    when {
        daysLeft == null -> Freshness.Unknown
        daysLeft < 0 -> Freshness.Expired
        // Today counts as one day left: zero would read as expired.
        daysLeft < soonDays -> Freshness.ExpiringSoon(daysLeft + 1)
        else -> Freshness.Fresh
    }

/** [soonDays] is a parameter and not a constant: how many days count as soon is a product setting. */
fun PantryItem.freshnessOn(
    now: Instant,
    zone: TimeZone,
    soonDays: Int,
): Freshness = freshnessIn(expiresAt?.let { daysUntilExpiry(it, now, zone) }, soonDays)

/** Past its date: from the day after the expiry date in [zone], never at an hour of the day itself. */
fun PantryItem.hasExpiredOn(
    now: Instant,
    zone: TimeZone,
): Boolean = expiresAt?.let { daysUntilExpiry(it, now, zone) < 0 } == true

private fun Instant.dateIn(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

private const val MILLIS_PER_DAY = 86_400_000L
