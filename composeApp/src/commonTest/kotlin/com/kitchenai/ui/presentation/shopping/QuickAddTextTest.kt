package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickAddTextTest {
    private val kg = unit("kg")
    private val piece = unit("piece")
    private val units = listOf(kg to setOf("kg"), piece to setOf("unidad", "unidades"))

    @Test
    fun `an amount and a unit lead the name`() {
        assertEquals(QuickAddText("potatoes", 2.0, kg), parseQuickAdd("2 kg potatoes", units))
    }

    @Test
    fun `the amount may touch the unit and use a comma`() {
        assertEquals(QuickAddText("patatas", 1.5, kg), parseQuickAdd("1,5kg patatas", units))
    }

    @Test
    fun `a plural spelling and its case and accents still find the unit`() {
        assertEquals(QuickAddText("huevos", 6.0, piece), parseQuickAdd("6 UNIDADES huevos", units))
    }

    @Test
    fun `an amount with no known unit keeps the word in the name`() {
        assertEquals(QuickAddText("lemons", 3.0, null), parseQuickAdd("3 lemons", units))
    }

    @Test
    fun `text that does not start with a number is all name`() {
        assertEquals(QuickAddText("milk 2 l"), parseQuickAdd("  milk 2 l ", units))
    }

    @Test
    fun `a number with nothing after it is a name`() {
        assertEquals(QuickAddText("7"), parseQuickAdd("7", units))
        assertEquals(QuickAddText("2 kg"), parseQuickAdd("2 kg", units))
    }

    private fun unit(id: String) = TermRef(TaxonomyId.of("units").value(), TermId.of(id).value())

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
