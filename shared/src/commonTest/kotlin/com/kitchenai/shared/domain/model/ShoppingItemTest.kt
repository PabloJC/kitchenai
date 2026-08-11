package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ShoppingItemTest {
    private val id = (ShoppingItemId.of("item") as AppResult.Success).data
    private val flour = (IngredientId.of("flour") as AppResult.Success).data
    private val now = Instant.fromEpochSeconds(1_000)

    @Test
    fun `a catalogue ingredient makes a valid unchecked line`() {
        val result = ShoppingItem.create(id = id, updatedAt = now, ingredient = flour)

        assertTrue(result is AppResult.Success)
        assertNull(result.data.freeText)
        assertFalse(result.data.checked)
    }

    @Test
    fun `a free-text line is valid on its own`() {
        val result = ShoppingItem.create(id = id, updatedAt = now, freeText = "the good bread")

        assertTrue(result is AppResult.Success)
        assertNull(result.data.ingredient)
    }

    @Test
    fun `a line with neither an ingredient nor free text is rejected`() {
        val result = ShoppingItem.create(id = id, updatedAt = now)

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is AppError.Validation)
    }

    @Test
    fun `a line with both an ingredient and free text is rejected`() {
        val result = ShoppingItem.create(id = id, updatedAt = now, ingredient = flour, freeText = "flour")

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is AppError.Validation)
    }

    @Test
    fun `blank free text counts as no free text`() {
        val result = ShoppingItem.create(id = id, updatedAt = now, freeText = "   ")

        assertTrue(result is AppResult.Failure)
        assertEquals("ingredient", (result.error as AppError.Validation).field)
    }
}
