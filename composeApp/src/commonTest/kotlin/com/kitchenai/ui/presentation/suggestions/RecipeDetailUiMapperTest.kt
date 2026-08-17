package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.LabelResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun line(
        ingredient: IngredientId? = null,
        freeText: String? = null,
        quantity: Quantity? = null,
    ): RecipeIngredient = RecipeIngredient(ingredient, freeText, quantity, optional = false)

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
