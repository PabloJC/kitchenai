package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppResult
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class FreshnessTest {
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val losAngeles = TimeZone.of("America/Los_Angeles")
    private val day = LocalDate(2026, 10, 14)
    private val soon = 3

    @Test
    fun `a holding with no expiry has unknown freshness`() {
        assertEquals(Freshness.Unknown, item(null).freshnessOn(at(day, 9, tokyo), tokyo, soon))
    }

    @Test
    fun `a holding expiring today is soon until local midnight and expired after it east and west of UTC`() {
        for (zone in listOf(tokyo, losAngeles)) {
            val holding = item(endOfDay(day, zone))

            assertEquals(Freshness.ExpiringSoon(1), holding.freshnessOn(at(day, 23, zone), zone, soon), zone.id)
            assertFalse(holding.hasExpiredOn(at(day, 23, zone), zone), zone.id)
            assertEquals(Freshness.Expired, holding.freshnessOn(at(day.next(), 0, zone), zone, soon), zone.id)
            assertTrue(holding.hasExpiredOn(at(day.next(), 0, zone), zone), zone.id)
        }
    }

    @Test
    fun `the hour of the day never moves an item between soon and fresh`() {
        val holding = item(endOfDay(LocalDate(2026, 10, 17), tokyo))

        // Three days ahead is fresh whether it is just after midnight or late in the evening.
        assertEquals(Freshness.Fresh, holding.freshnessOn(at(day, 0, tokyo), tokyo, soon))
        assertEquals(Freshness.Fresh, holding.freshnessOn(at(day, 23, tokyo), tokyo, soon))
        assertEquals(Freshness.ExpiringSoon(3), holding.freshnessOn(at(LocalDate(2026, 10, 15), 0, tokyo), tokyo, soon))
    }

    @Test
    fun `a date stored as UTC midnight counts as its own day in any zone`() {
        val legacy = item(day.atStartOfDayIn(TimeZone.UTC))

        for (zone in listOf(tokyo, losAngeles)) {
            assertEquals(day, legacy.expiresAt?.expiryDate(zone), zone.id)
            assertEquals(Freshness.ExpiringSoon(1), legacy.freshnessOn(at(day, 20, zone), zone, soon), zone.id)
            assertFalse(legacy.hasExpiredOn(at(day, 20, zone), zone), zone.id)
        }
    }

    @Test
    fun `the soon window is a parameter so the same holding reads differently under a shorter one`() {
        val holding = item(endOfDay(LocalDate(2026, 10, 16), tokyo))

        assertEquals(Freshness.ExpiringSoon(3), holding.freshnessOn(at(day, 9, tokyo), tokyo, soon))
        assertEquals(Freshness.Fresh, holding.freshnessOn(at(day, 9, tokyo), tokyo, 1))
    }

    private fun at(
        date: LocalDate,
        hour: Int,
        zone: TimeZone,
    ): Instant = LocalDateTime(date, LocalTime(hour, 30)).toInstant(zone)

    // How the pantry stores a picked date: the last millisecond of that day in the device zone.
    private fun endOfDay(
        date: LocalDate,
        zone: TimeZone,
    ): Instant = date.next().atStartOfDayIn(zone) - 1.milliseconds

    private fun LocalDate.next(): LocalDate = plus(1, DateTimeUnit.DAY)

    private fun item(expiresAt: Instant?): PantryItem =
        PantryItem(
            id = (PantryItemId.of("item-1") as AppResult.Success).data,
            ingredient = (IngredientId.of("ing-1") as AppResult.Success).data,
            freeText = null,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = expiresAt,
            updatedAt = Instant.fromEpochSeconds(0),
        )
}
