package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.LabelResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecipeDetailUiMapperTest {
    private val unitRef = TermRef(TaxonomyId.of("units").value(), TermId.of("gram").value())
    private val ingredientId = IngredientId.of("rice").value()

    @Test
    fun `a catalogue line resolves its ingredient and unit names`() {
        val resolver =
            LabelResolver(
                terms = listOf(Term(unitRef, mapOf("en" to "g"), null, 0)),
                ingredients = listOf(Ingredient(ingredientId, mapOf("en" to "Rice"), null, emptyList())),
                languageTags = listOf("en"),
            )
        val line = line(ingredient = ingredientId, quantity = Quantity(200.0, unitRef))

        val ui = line.toUi(resolver)

        assertEquals("Rice", ui.name)
        assertEquals("200 g", ui.quantity)
    }

    @Test
    fun `a free-text line keeps its own wording`() {
        val ui = line(freeText = "Pinch of salt").toUi(LabelResolver())

        assertEquals("Pinch of salt", ui.name)
    }

    @Test
    fun `a catalogue miss falls back to the identifier`() {
        val ui = line(ingredient = ingredientId).toUi(LabelResolver())

        assertEquals(ingredientId.value, ui.name)
    }

    @Test
    fun `no quantity renders no quantity`() {
        val ui = line(ingredient = ingredientId, quantity = null).toUi(LabelResolver())

        assertNull(ui.quantity)
    }

    @Test
    fun `a candidate holding names itself rather than asking the resolver`() {
        val holding = holding("item-1", "the good bread")

        val ui = line(freeText = "bread").toUi(LabelResolver(), candidates = listOf(holding))

        assertEquals("the good bread", ui.candidates.single().label)
    }

    @Test
    fun `a candidate is marked confirmed only when its id is in the confirmed set`() {
        val confirmed = holding("item-1", "the good bread")
        val notConfirmed = holding("item-2", "rye bread")

        val ui =
            line(freeText = "bread").toUi(
                LabelResolver(),
                candidates = listOf(confirmed, notConfirmed),
                confirmed = setOf(confirmed.id),
            )

        assertTrue(ui.candidates.single { it.id == confirmed.id }.confirmed)
        assertFalse(ui.candidates.single { it.id == notConfirmed.id }.confirmed)
    }

    @Test
    fun `a line with no candidates renders none`() {
        val ui = line(ingredient = ingredientId).toUi(LabelResolver())

        assertTrue(ui.candidates.isEmpty())
    }

    private fun holding(
        id: String,
        freeText: String,
    ): PantryItem =
        PantryItem(
            id = PantryItemId.of(id).value(),
            ingredient = null,
            freeText = freeText,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = null,
            updatedAt = Instant.fromEpochSeconds(0),
        )

    private fun line(
        ingredient: IngredientId? = null,
        freeText: String? = null,
        quantity: Quantity? = null,
    ): RecipeIngredient = RecipeIngredient(ingredient, freeText, quantity, optional = false)

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
