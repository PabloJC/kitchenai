package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuantityTest {
    private val grams = termRef("unit", "gram")
    private val millilitres = termRef("unit", "millilitre")

    @Test
    fun `quantities with the same unit add up`() {
        val sum = Quantity(200.0, grams) + Quantity(50.0, grams)
        assertTrue(sum is AppResult.Success)
        assertEquals(Quantity(250.0, grams), sum.data)
    }

    @Test
    fun `quantities with the same unit subtract`() {
        val rest = Quantity(200.0, grams) - Quantity(50.0, grams)
        assertTrue(rest is AppResult.Success)
        assertEquals(Quantity(150.0, grams), rest.data)
    }

    @Test
    fun `unitless quantities are counts and combine with each other`() {
        val sum = Quantity(3.0) + Quantity(2.0)
        assertTrue(sum is AppResult.Success)
        assertEquals(Quantity(5.0, null), sum.data)
    }

    @Test
    fun `adding a unitless quantity to a unit one fails`() {
        val sum = Quantity(3.0) + Quantity(200.0, grams)
        assertTrue(sum is AppResult.Failure)
        assertTrue(sum.error is AppError.Validation)
    }

    @Test
    fun `arithmetic across different units fails and converts nothing`() {
        val sum = Quantity(200.0, grams) + Quantity(50.0, millilitres)
        assertTrue(sum is AppResult.Failure)
        assertEquals("unit", (sum.error as AppError.Validation).field)

        val rest = Quantity(200.0, grams) - Quantity(50.0, millilitres)
        assertTrue(rest is AppResult.Failure)
    }

    @Test
    fun `canCombineWith answers on the unit alone`() {
        assertTrue(Quantity(1.0, grams).canCombineWith(Quantity(999.0, grams)))
        assertTrue(Quantity(1.0).canCombineWith(Quantity(999.0)))
        assertFalse(Quantity(1.0, grams).canCombineWith(Quantity(1.0, millilitres)))
        assertFalse(Quantity(1.0, grams).canCombineWith(Quantity(1.0)))
    }

    private fun termRef(
        taxonomy: String,
        term: String,
    ): TermRef =
        TermRef(
            (TaxonomyId.of(taxonomy) as AppResult.Success).data,
            (TermId.of(term) as AppResult.Success).data,
        )
}
