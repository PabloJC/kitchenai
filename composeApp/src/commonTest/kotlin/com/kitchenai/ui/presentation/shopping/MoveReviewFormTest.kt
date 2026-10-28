package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MoveReviewFormTest {
    private val rice = line("rice", amount = 200.0)
    private val milk = line("milk", amount = null)
    private val monday = Instant.fromEpochSeconds(86_400)
    private val friday = Instant.fromEpochSeconds(5 * 86_400)

    @Test
    fun `same date for all dates every line and a later pick overrides one of them`() {
        val form = MoveReviewForm()

        form.setAll(monday, listOf(rice, milk))
        form.setDate(milk.id, friday)

        assertEquals(mapOf(rice.id to monday, milk.id to friday), form.options(listOf(rice, milk), true).expiresAt)
    }

    @Test
    fun `skip dates sends no dates but keeps the amount typed for a bare line`() {
        val form = MoveReviewForm()
        form.setAll(monday, listOf(rice, milk))
        form.setAmount(milk.id, 2.0)

        val options = form.options(listOf(rice, milk), withDates = false)

        assertTrue(options.expiresAt.isEmpty())
        assertTrue(options.defaultAmountForBare)
        assertEquals(mapOf(milk.id to 2.0), options.bareAmounts)
    }

    @Test
    fun `a line that left the cart while the sheet was open drops out`() {
        val form = MoveReviewForm()
        form.setAll(monday, listOf(rice, milk))
        form.setAmount(milk.id, 2.0)

        val options = form.options(listOf(rice), withDates = true)

        assertEquals(mapOf(rice.id to monday), options.expiresAt)
        assertTrue(options.bareAmounts.isEmpty())
    }

    @Test
    fun `an amount that is not a number or not above zero blocks the move`() {
        val form = MoveReviewForm()
        assertTrue(form.canMove(listOf(milk)))

        form.setMalformed(milk.id, true)
        assertFalse(form.canMove(listOf(milk)))

        form.setMalformed(milk.id, false)
        form.setAmount(milk.id, 0.0)
        assertFalse(form.canMove(listOf(milk)))

        form.setAmount(milk.id, null)
        assertTrue(form.canMove(listOf(milk)))
    }

    private fun line(
        id: String,
        amount: Double?,
    ) = ShoppingItemUi(
        id = (ShoppingItemId.of(id) as AppResult.Success).data,
        label = id,
        quantity = amount?.toString(),
        amount = amount,
        fromCatalogue = true,
        checked = true,
    )
}
