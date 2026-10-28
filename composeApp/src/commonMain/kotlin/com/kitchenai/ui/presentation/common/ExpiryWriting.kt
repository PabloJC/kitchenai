package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.domain.model.expiryDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/*
 * Expiry is a calendar date stored as the last instant of that day in the device's zone. Reading it
 * back as a day, and grouping by it, is the domain's (`ExpiryDay.kt`); writing it is this file's.
 */

/** The last millisecond of [date] in [zone]: the moment it stops being that day there. */
internal fun endOfDay(
    date: LocalDate,
    zone: TimeZone,
): Instant = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - 1.milliseconds

/** The date this instant falls on in [zone], with no reading of how it was stored. */
private fun Instant.dateIn(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

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
