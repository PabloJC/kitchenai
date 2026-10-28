package com.kitchenai.shared.domain.usecase.reminder

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ReminderSettings
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlanExpiryDigestsUseCaseTest {
    private val zone = TimeZone.of("Europe/Madrid")
    private val plan = PlanExpiryDigestsUseCase()

    @Test
    fun `an item expiring tomorrow is announced at today's chosen time and again on its day`() {
        val now = at(2026, 10, 20, 9)
        val items = listOf(item("a", at(2026, 10, 21, 12)))

        val digests = plan(items, ReminderSettings(), now, zone)

        assertEquals(listOf(at(2026, 10, 20, 18), at(2026, 10, 21, 18)), digests.map { it.fireAt })
        assertEquals(1, digests[0].expiringCount)
        assertEquals(0, digests[0].expiredCount)
        // Still good until midnight on its own day, so expiring rather than expired.
        assertEquals(1, digests[1].expiringCount)
    }

    @Test
    fun `past today's time the first reminder moves to tomorrow`() {
        val now = at(2026, 10, 20, 19)
        val items = listOf(item("a", at(2026, 10, 21, 23)))

        assertEquals(at(2026, 10, 21, 18), plan(items, ReminderSettings(), now, zone).first().fireAt)
    }

    @Test
    fun `expired and expiring are counted apart and later dates wait for their own day`() {
        val now = at(2026, 10, 20, 9)
        val items =
            listOf(
                item("gone", at(2026, 10, 19, 12)),
                item("soon", at(2026, 10, 21, 10)),
                item("later", at(2026, 10, 25, 10)),
                item("undated", null),
            )

        val digests = plan(items, ReminderSettings(), now, zone)

        val days = listOf(at(2026, 10, 20, 18), at(2026, 10, 21, 18), at(2026, 10, 24, 18), at(2026, 10, 25, 18))
        assertEquals(days, digests.map { it.fireAt })
        assertEquals(1, digests[0].expiredCount)
        assertEquals(1, digests[0].expiringCount)
        assertEquals(2, digests[2].expiredCount)
        assertEquals(1, digests[2].expiringCount)
    }

    @Test
    fun `days with only past-date items after the first stay quiet`() {
        val items = listOf(item("gone", at(2026, 10, 19, 12)))

        val digests = plan(items, ReminderSettings(), at(2026, 10, 20, 9), zone)

        assertEquals(listOf(at(2026, 10, 20, 18)), digests.map { it.fireAt })
    }

    @Test
    fun `nothing within the week means no reminder`() {
        val items = listOf(item("far", at(2026, 11, 10, 10)), item("undated", null))

        assertTrue(plan(items, ReminderSettings(), at(2026, 10, 20, 9), zone).isEmpty())
    }

    @Test
    fun `the plan reaches a week ahead and no further`() {
        val items = (21..30).map { day -> item("d$day", at(2026, 10, day, 12)) }

        val digests = plan(items, ReminderSettings(), at(2026, 10, 20, 9), zone)

        assertEquals(PlanExpiryDigestsUseCase.HORIZON_DAYS, digests.size)
        assertEquals(at(2026, 10, 26, 18), digests.last().fireAt)
    }

    @Test
    fun `reminders turned off plan nothing`() {
        val items = listOf(item("a", at(2026, 10, 20, 20)))

        assertTrue(plan(items, ReminderSettings(enabled = false), at(2026, 10, 20, 9), zone).isEmpty())
    }

    @Test
    fun `the chosen time is honoured in the device zone`() {
        val settings = ReminderSettings(hour = 7, minute = 30)
        val items = listOf(item("a", at(2026, 10, 21, 7)))

        val digest = plan(items, settings, at(2026, 10, 20, 9), zone).first()

        assertEquals(LocalDateTime(2026, 10, 21, 7, 30).toInstant(zone), digest.fireAt)
    }

    @Test
    fun `the expiring items travel with the digest soonest first`() {
        val now = at(2026, 10, 20, 9)
        val items = listOf(item("tomorrow", at(2026, 10, 21, 12)), item("today", at(2026, 10, 20, 22)))

        val first = plan(items, ReminderSettings(), now, zone).first()

        assertEquals(listOf("today", "tomorrow"), first.expiring.map { it.id.value })
    }

    @Test
    fun `today reads the pantry as it is now and says nothing when nothing is due`() {
        val now = at(2026, 10, 20, 18)

        val due =
            plan.today(
                listOf(item("a", at(2026, 10, 21, 9)), item("gone", at(2026, 10, 18, 9))),
                ReminderSettings(),
                now,
                zone,
            )
        val quiet = plan.today(listOf(item("later", at(2026, 10, 25, 9))), ReminderSettings(), now, zone)

        assertEquals(now, due?.fireAt)
        assertEquals(1, due?.expiringCount)
        assertEquals(1, due?.expiredCount)
        assertEquals(null, quiet)
    }

    @Test
    fun `today with reminders off says nothing`() {
        val items = listOf(item("a", at(2026, 10, 21, 9)))

        assertEquals(null, plan.today(items, ReminderSettings(enabled = false), at(2026, 10, 20, 18), zone))
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
    ): Instant = LocalDateTime(year, month, day, hour, 0).toInstant(zone)

    private fun item(
        id: String,
        expiresAt: Instant?,
    ): PantryItem =
        PantryItem(
            id = (PantryItemId.of(id) as AppResult.Success).data,
            ingredient = (IngredientId.of("ing-$id") as AppResult.Success).data,
            freeText = null,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = expiresAt,
            updatedAt = Instant.fromEpochSeconds(0),
        )
}
