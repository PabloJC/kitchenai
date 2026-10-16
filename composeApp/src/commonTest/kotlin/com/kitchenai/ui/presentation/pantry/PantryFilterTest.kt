package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Freshness
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.test.Test
import kotlin.test.assertEquals

class PantryFilterTest {
    private val fridge = place("fridge")
    private val cupboard = place("cupboard")
    private val rows =
        listOf(
            row("1", "Comino molido", cupboard),
            row("2", "Limón", fridge),
            row("3", "Leche", fridge),
            row("4", "Sal", null),
        )

    @Test
    fun `no search and no chip keep every row`() {
        assertEquals(rows, rows.filteredBy("", null))
    }

    @Test
    fun `the name matches anywhere ignoring case and accents`() {
        assertEquals(listOf("1"), rows.filteredBy("COMINO", null).ids())
        assertEquals(listOf("2"), rows.filteredBy("limon", null).ids())
        assertEquals(listOf("1"), rows.filteredBy("molido", null).ids())
    }

    @Test
    fun `surrounding spaces in the search are ignored`() {
        assertEquals(listOf("4"), rows.filteredBy("  sal ", null).ids())
    }

    @Test
    fun `a location chip and a search combine`() {
        assertEquals(listOf("2", "3"), rows.filteredBy("", fridge).ids())
        assertEquals(listOf("3"), rows.filteredBy("le", fridge).ids())
        assertEquals(emptyList(), rows.filteredBy("comino", fridge).ids())
    }

    @Test
    fun `only locations some row uses are offered as chips`() {
        val offered = listOf(fridge to "Fridge", place("freezer") to "Freezer", cupboard to "Cupboard")

        assertEquals(listOf(fridge to "Fridge", cupboard to "Cupboard"), offered.usedBy(rows))
    }

    private fun List<PantryItemUi>.ids() = map { row -> row.id.value }

    private fun place(id: String) = TermRef(TaxonomyId.of("locations").value(), TermId.of(id).value())

    private fun row(
        id: String,
        name: String,
        location: TermRef?,
    ) = PantryItemUi(
        id = PantryItemId.of(id).value(),
        ingredient = null,
        freeText = name,
        name = name,
        quantityLabel = "1",
        amount = 1.0,
        unit = null,
        location = location,
        locationLabel = null,
        expiresAt = null,
        freshness = Freshness.Unknown,
    )

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
