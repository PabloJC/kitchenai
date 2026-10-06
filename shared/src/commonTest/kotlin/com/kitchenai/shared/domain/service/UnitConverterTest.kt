package com.kitchenai.shared.domain.service

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.UnitDimension
import com.kitchenai.shared.domain.usecase.profile.metricUnitTerms
import com.kitchenai.shared.domain.usecase.profile.unitTerm
import com.kitchenai.shared.domain.usecase.shopping.termRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitConverterTest {
    private val gram = termRef("units", "gram")
    private val kilogram = termRef("units", "kilogram")
    private val millilitre = termRef("units", "millilitre")
    private val litre = termRef("units", "litre")
    private val tablespoon = termRef("units", "tablespoon")
    private val piece = termRef("units", "piece")
    private val converter = UnitConverter(metricUnitTerms("units"))

    @Test
    fun `converts both ways within mass`() {
        assertEquals(AppResult.Success(Quantity(0.5, kilogram)), converter.convert(Quantity(500.0, gram), kilogram))
        assertEquals(AppResult.Success(Quantity(500.0, gram)), converter.convert(Quantity(0.5, kilogram), gram))
    }

    @Test
    fun `converts both ways within volume and between a spoon and a litre`() {
        assertEquals(AppResult.Success(Quantity(0.25, litre)), converter.convert(Quantity(250.0, millilitre), litre))
        assertEquals(
            AppResult.Success(Quantity(30.0, millilitre)),
            converter.convert(Quantity(2.0, tablespoon), millilitre),
        )
        assertEquals(AppResult.Success(Quantity(0.03, litre)), converter.convert(Quantity(2.0, tablespoon), litre))
    }

    @Test
    fun `mass to volume is rejected because it needs a density`() {
        assertRejected(converter.convert(Quantity(100.0, gram), millilitre))
        assertRejected(converter.convert(Quantity(100.0, litre), kilogram))
    }

    @Test
    fun `a piece converts to nothing and nothing converts to a piece`() {
        assertRejected(converter.convert(Quantity(1.0, piece), gram))
        assertRejected(converter.convert(Quantity(1.0, gram), piece))
        assertRejected(converter.convert(Quantity(1.0, piece), piece))
    }

    @Test
    fun `a count with no unit is rejected`() {
        assertRejected(converter.convert(Quantity(3.0), gram))
    }

    @Test
    fun `a unit the catalogue does not know is rejected on either side`() {
        val unknown = termRef("units", "stone")

        assertRejected(converter.convert(Quantity(1.0, unknown), gram))
        assertRejected(converter.convert(Quantity(1.0, gram), unknown))
    }

    @Test
    fun `a unit with no conversion data is rejected even next to one that has some`() {
        val pinch = unitTerm("units", "pinch")
        val withPinch = UnitConverter(metricUnitTerms("units") + pinch)

        assertRejected(withPinch.convert(Quantity(1.0, pinch.ref), gram))
        assertRejected(withPinch.convert(Quantity(1.0, gram), pinch.ref))
    }

    @Test
    fun `a factor that is not a positive finite number leaves the unit unconvertible`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { factor ->
            val broken = unitTerm("units", "broken", UnitDimension.MASS, factor)
            val withBroken = UnitConverter(metricUnitTerms("units") + broken)

            assertRejected(withBroken.convert(Quantity(1.0, gram), broken.ref))
            assertRejected(withBroken.convert(Quantity(1.0, broken.ref), gram))
        }
    }

    @Test
    fun `a converter with no units converts nothing`() {
        assertRejected(UnitConverter.NONE.convert(Quantity(1.0, gram), kilogram))
    }

    @Test
    fun `amountIn is the amount itself in the same unit and null when incomparable`() {
        assertEquals(5.0, converter.amountIn(Quantity(5.0, gram), gram))
        assertEquals(5.0, converter.amountIn(Quantity(5.0), null))
        assertEquals(0.005, converter.amountIn(Quantity(5.0, gram), kilogram))
        assertNull(converter.amountIn(Quantity(5.0, gram), null))
        assertNull(converter.amountIn(Quantity(5.0), gram))
        assertNull(converter.amountIn(Quantity(5.0, gram), millilitre))
        assertEquals(5.0, UnitConverter.NONE.amountIn(Quantity(5.0, gram), gram))
        assertNull(UnitConverter.NONE.amountIn(Quantity(5.0, gram), kilogram))
    }

    private fun assertRejected(result: AppResult<Quantity>) {
        assertTrue(result is AppResult.Failure)
        assertEquals("unit", (result.error as AppError.Validation).field)
    }
}
