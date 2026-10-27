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
import kotlin.test.assertNull
import kotlin.time.Instant

class PlanExpiryDigestUseCaseTest {
    private val zone = TimeZone.of("Europe/Madrid")
    private val plan = PlanExpiryDigestUseCase()

    @Test
    fun `an item expiring tomorrow is announced at today's chosen time`() {
        val now = at(2026, 10, 20, 9)
        val items = listOf(item("a", at(2026, 10, 21, 12)))

        val digest = plan(items, ReminderSettings(), now, zone)

        assertEquals(at(2026, 10, 20, 18), digest?.fireAt)
        assertEquals(1, digest?.expiringCount)
        assertEquals(0, digest?.expiredCount)
    }

    @Test
    fun `past today's time the reminder moves to tomorrow`() {
        val now = at(2026, 10, 20, 19)
        val items = listOf(item("a", at(2026, 10, 21, 23)))

        assertEquals(at(2026, 10, 21, 18), plan(items, ReminderSettings(), now, zone)?.fireAt)
    }

    @Test
    fun `expired and expiring are counted apart and later dates are left out`() {
        val now = at(2026, 10, 20, 9)
        val items =
            listOf(
                item("gone", at(2026, 10, 19, 12)),
                item("soon", at(2026, 10, 21, 10)),
                item("later", at(2026, 10, 25, 10)),
                item("undated", null),
            )

        val digest = plan(items, ReminderSettings(), now, zone)

        assertEquals(1, digest?.expiredCount)
        assertEquals(1, digest?.expiringCount)
    }

    @Test
    fun `nothing within a day means no reminder`() {
        val items = listOf(item("later", at(2026, 10, 25, 10)), item("undated", null))

        assertNull(plan(items, ReminderSettings(), at(2026, 10, 20, 9), zone))
    }

    @Test
    fun `reminders turned off plan nothing`() {
        val items = listOf(item("a", at(2026, 10, 20, 20)))

        assertNull(plan(items, ReminderSettings(enabled = false), at(2026, 10, 20, 9), zone))
    }

    @Test
    fun `the chosen time is honoured in the device zone`() {
        val settings = ReminderSettings(hour = 7, minute = 30)
        val items = listOf(item("a", at(2026, 10, 21, 7)))

        val digest = plan(items, settings, at(2026, 10, 20, 9), zone)

        assertEquals(LocalDateTime(2026, 10, 21, 7, 30).toInstant(zone), digest?.fireAt)
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
