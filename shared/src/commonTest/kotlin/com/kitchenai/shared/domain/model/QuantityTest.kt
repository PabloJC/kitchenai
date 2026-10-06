package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.service.UnitConverter
import com.kitchenai.shared.domain.usecase.profile.metricUnitConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuantityTest {
    private val grams = termRef("unit", "gram")
    private val millilitres = termRef("unit", "millilitre")
    private val kilograms = termRef("unit", "kilogram")
    private val litres = termRef("unit", "litre")
    private val pieces = termRef("unit", "piece")
    private val converter = metricUnitConverter("unit")

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

    @Test
    fun `a convertible right-hand side is added in the left-hand unit`() {
        val sum = Quantity(1.0, kilograms).plus(Quantity(500.0, grams), converter)
        assertEquals(AppResult.Success(Quantity(1.5, kilograms)), sum)

        val reverse = Quantity(500.0, grams).plus(Quantity(1.0, kilograms), converter)
        assertEquals(AppResult.Success(Quantity(1500.0, grams)), reverse)
    }

    @Test
    fun `a convertible right-hand side is subtracted in the left-hand unit`() {
        val rest = Quantity(1.0, litres).minus(Quantity(250.0, millilitres), converter)

        assertEquals(AppResult.Success(Quantity(0.75, litres)), rest)
    }

    @Test
    fun `subtracting the same amount in another unit leaves exactly zero`() {
        // 4.03 kg is 4030.0000000000005 g in floating point; it must not leave a negative sliver.
        val rest = Quantity(4030.0, grams).minus(Quantity(4.03, kilograms), converter)

        assertEquals(AppResult.Success(Quantity(0.0, grams)), rest)
    }

    @Test
    fun `with a converter the same unit still combines as before`() {
        assertEquals(
            AppResult.Success(Quantity(250.0, grams)),
            Quantity(200.0, grams).plus(Quantity(50.0, grams), converter),
        )
        assertEquals(AppResult.Success(Quantity(5.0)), Quantity(3.0).plus(Quantity(2.0), converter))
    }

    @Test
    fun `a converter does not make incompatible units combine`() {
        listOf(
            Quantity(1.0, grams) to Quantity(1.0, millilitres),
            Quantity(1.0, grams) to Quantity(1.0, pieces),
            Quantity(1.0, pieces) to Quantity(1.0, grams),
            Quantity(1.0, grams) to Quantity(1.0),
            Quantity(1.0) to Quantity(1.0, grams),
        ).forEach { (left, right) ->
            val sum = left.plus(right, converter)
            assertTrue(sum is AppResult.Failure)
            assertEquals("unit", (sum.error as AppError.Validation).field)
            assertTrue(left.minus(right, converter) is AppResult.Failure)
            assertFalse(left.canCombineWith(right, converter))
        }
    }

    @Test
    fun `a converter that knows no units combines exactly what the operators do`() {
        assertTrue(Quantity(1.0, kilograms).plus(Quantity(500.0, grams), UnitConverter.NONE) is AppResult.Failure)
        assertTrue(Quantity(1.0, kilograms).canCombineWith(Quantity(1.0, kilograms), UnitConverter.NONE))
    }

    @Test
    fun `canCombineWith with a converter accepts the same dimension in either direction`() {
        assertTrue(Quantity(1.0, kilograms).canCombineWith(Quantity(1.0, grams), converter))
        assertTrue(Quantity(1.0, grams).canCombineWith(Quantity(1.0, kilograms), converter))
        assertTrue(Quantity(1.0, pieces).canCombineWith(Quantity(2.0, pieces), converter))
        assertFalse(Quantity(1.0, litres).canCombineWith(Quantity(1.0, kilograms), converter))
    }

    @Test
    fun `reaches tolerates the rounding a conversion leaves but not a real shortfall`() {
        assertTrue(0.30000000000000004.reaches(0.3))
        assertTrue(0.3.reaches(0.30000000000000004))
        assertFalse(0.299.reaches(0.3))
        assertTrue(5.0.reaches(0.0))
    }

    @Test
    fun `ceilToWhole rounds up but lets a hair above a whole number stay on it`() {
        assertEquals(1.0, 0.2.ceilToWhole())
        assertEquals(3.0, 3.0000000000000004.ceilToWhole())
        assertEquals(4.0, 3.1.ceilToWhole())
        assertEquals(1.0, 1e-12.ceilToWhole())
        assertEquals(0.0, 0.0.ceilToWhole())
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
