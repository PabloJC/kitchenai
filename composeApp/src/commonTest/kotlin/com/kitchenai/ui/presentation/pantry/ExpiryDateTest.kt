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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpiryDateTest {
    private val madrid = TimeZone.of("Europe/Madrid")
    private val newYork = TimeZone.of("America/New_York")
    private val day = LocalDate(2026, 10, 14)

    @Test
    fun `a day ends at local midnight not at UTC midnight`() {
        // Madrid is UTC+2 in October.
        assertEquals(
            LocalDateTime(2026, 10, 14, 21, 59, 59, 999_000_000).toInstant(TimeZone.UTC),
            endOfDay(day, madrid),
        )
    }

    @Test
    fun `the picked day is kept on both sides of UTC`() {
        val pickerMillis = day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

        assertEquals(day, expiryFromPicker(pickerMillis, madrid).expiryDate(madrid))
        // UTC midnight is the previous evening in New York: read raw it would show the 13th.
        assertEquals(day, expiryFromPicker(pickerMillis, newYork).expiryDate(newYork))
    }

    @Test
    fun `the picker reopens on the stored day`() {
        val pickerMillis = day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

        assertEquals(pickerMillis, expiryFromPicker(pickerMillis, newYork).toPickerMillis(newYork))
    }

    @Test
    fun `an item expiring today stays use soon until local midnight`() {
        val expiresAt = endOfDay(day, madrid)
        val lateTonight = LocalDateTime(2026, 10, 14, 23, 30).toInstant(madrid)
        val justAfterMidnight = LocalDateTime(2026, 10, 15, 0, 30).toInstant(madrid)

        assertEquals(0, daysUntilExpiry(expiresAt, lateTonight, madrid))
        assertEquals(Freshness.ExpiringSoon(1), freshnessIn(daysUntilExpiry(expiresAt, lateTonight, madrid)))
        assertEquals(Freshness.Expired, freshnessIn(daysUntilExpiry(expiresAt, justAfterMidnight, madrid)))
    }

    @Test
    fun `a date stored as UTC midnight still reads as its own day in Madrid`() {
        // How expiry was written before end-of-day storage.
        val legacy = day.atStartOfDayIn(TimeZone.UTC)
        val morning = LocalDateTime(2026, 10, 14, 10, 0).toInstant(madrid)

        assertEquals(0, daysUntilExpiry(legacy, morning, madrid))
    }

    @Test
    fun `soon covers today and the next two days only`() {
        assertEquals(Freshness.ExpiringSoon(3), freshnessIn(2))
        assertEquals(Freshness.Fresh, freshnessIn(3))
        assertEquals(Freshness.Unknown, freshnessIn(null))
    }

    @Test
    fun `labels name today tomorrow and yesterday and count the rest`() {
        assertEquals(UiText.of(Res.string.pantry_expires_today), expiryLabel(0))
        assertEquals(UiText.of(Res.string.pantry_expires_tomorrow), expiryLabel(1))
        assertEquals(UiText.of(Res.string.pantry_expired_yesterday), expiryLabel(-1))
        assertEquals(UiText.Plural(Res.plurals.pantry_expires_in_days, 5), expiryLabel(5))
        assertEquals(UiText.Plural(Res.plurals.pantry_expired_days_ago, 3), expiryLabel(-3))
    }

    @Test
    fun `a quick choice counts from today in the device zone`() {
        val now = LocalDateTime(2026, 1, 31, 23, 30).toInstant(madrid)

        assertEquals(LocalDate(2026, 2, 3), expiryIn(3, DateTimeUnit.DAY, now, madrid).expiryDate(madrid))
        assertEquals(LocalDate(2026, 2, 28), expiryIn(1, DateTimeUnit.MONTH, now, madrid).expiryDate(madrid))
    }
}
