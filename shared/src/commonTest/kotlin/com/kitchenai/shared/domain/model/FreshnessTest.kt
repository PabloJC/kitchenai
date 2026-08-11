package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class FreshnessTest {
    private val now = Instant.fromEpochSeconds(1_000_000)
    private val window = 3.days

    @Test
    fun `a holding with no expiry has unknown freshness`() {
        assertEquals(Freshness.Unknown, item(null).freshnessAt(now, window))
    }

    @Test
    fun `a holding at or past its expiry is expired`() {
        assertEquals(Freshness.Expired, item(now - 1.hours).freshnessAt(now, window))
        assertEquals(Freshness.Expired, item(now).freshnessAt(now, window))
    }

    @Test
    fun `a holding inside the window is expiring soon and a partial day counts as one`() {
        assertEquals(Freshness.ExpiringSoon(2), item(now + 2.days).freshnessAt(now, window))
        assertEquals(Freshness.ExpiringSoon(1), item(now + 6.hours).freshnessAt(now, window))
    }

    @Test
    fun `a holding beyond the window is fresh`() {
        assertEquals(Freshness.Fresh, item(now + 10.days).freshnessAt(now, window))
    }

    @Test
    fun `the window is a parameter so the same holding reads differently under a shorter one`() {
        assertEquals(Freshness.Fresh, item(now + 2.days).freshnessAt(now, 1.days))
    }

    private fun item(expiresAt: Instant?): PantryItem =
        PantryItem(
            id = (PantryItemId.of("item-1") as AppResult.Success).data,
            ingredient = (IngredientId.of("ing-1") as AppResult.Success).data,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = expiresAt,
            updatedAt = now,
        )
}
