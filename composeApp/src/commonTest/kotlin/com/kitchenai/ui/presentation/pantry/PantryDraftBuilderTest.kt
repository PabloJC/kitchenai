package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Freshness
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PantryDraftBuilderTest {
    private val gram = ref("gram")
    private val litre = ref("litre")
    private val salt = IngredientOption(ingredient("salt"), "Salt", gram)
    private val units = listOf(gram to "g", litre to "l")

    @Test
    fun `a pick alone is savable as one of whatever unit is shown`() {
        val draft = draftOf(salt, "Salt", null, false, gram, null, null)

        assertEquals(salt.id, draft?.ingredient)
        assertNull(draft?.freeText)
        assertEquals(1.0, draft?.amount)
        assertEquals(gram, draft?.unit)
    }

    @Test
    fun `typed text with nothing picked becomes free text`() {
        val draft = draftOf(null, "  the good bread ", 2.0, false, litre, null, null)

        assertNull(draft?.ingredient)
        assertEquals("the good bread", draft?.freeText)
        assertEquals(2.0, draft?.amount)
        assertEquals(litre, draft?.unit)
    }

    @Test
    fun `nothing is savable without a source`() {
        assertNull(draftOf(null, "   ", 2.0, false, gram, null, null))
    }

    @Test
    fun `a malformed amount is never replaced by the default`() {
        assertNull(draftOf(salt, "Salt", null, true, null, null, null))
    }

    @Test
    fun `the unit the user chose wins over the ingredient's own`() {
        assertEquals(litre, shownUnit("litre", salt, units))
    }

    @Test
    fun `an untouched selector shows the ingredient's own unit`() {
        assertEquals(gram, shownUnit(null, salt, listOf(litre to "l", gram to "g")))
    }

    @Test
    fun `an ingredient without a default shows the first unit on offer`() {
        assertEquals(litre, shownUnit(null, salt.copy(defaultUnit = null), units.reversed()))
    }

    @Test
    fun `a default unit the vocabulary does not list is not shown`() {
        assertEquals(litre, shownUnit(null, salt, listOf(litre to "l")))
    }

    @Test
    fun `a pick that the catalogue still lists resolves to its option`() {
        assertEquals(salt, pickedOption("salt", listOf(salt), null))
    }

    @Test
    fun `an ingredient the catalogue dropped is still the row's own`() {
        val id = ingredient("gone")

        val option = pickedOption("gone", emptyList(), itemUi(id, "Gone"))

        assertEquals(id, option?.id)
        assertEquals("Gone", option?.label)
    }

    @Test
    fun `an id that names nothing resolves to nothing`() {
        assertNull(pickedOption("other", listOf(salt), null))
    }

    private fun ref(term: String) = TermRef(TaxonomyId.of("units").value(), TermId.of(term).value())

    private fun ingredient(id: String) = IngredientId.of(id).value()

    private fun itemUi(
        ingredient: IngredientId,
        name: String,
    ) = PantryItemUi(
        id = PantryItemId.of("item").value(),
        ingredient = ingredient,
        freeText = null,
        name = name,
        quantityLabel = "1",
        amount = 1.0,
        unit = null,
        location = null,
        locationLabel = null,
        expiresAt = null,
        freshness = Freshness.Unknown,
    )

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
