package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.LabelResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ShoppingUiMapperTest {
    private val unitRef = TermRef(TaxonomyId.of("units").value(), TermId.of("gram").value())
    private val ingredientId = IngredientId.of("rice").value()
    private val now = Instant.fromEpochSeconds(1_000)

    @Test
    fun `a catalogue line resolves its ingredient name and quantity`() {
        val resolver =
            LabelResolver(
                terms = listOf(Term(unitRef, mapOf("en" to "g"), null, 0)),
                ingredients = listOf(Ingredient(ingredientId, mapOf("en" to "Rice"), null, emptyList())),
                languageTags = listOf("en"),
            )
        val item = item(ingredient = ingredientId, quantity = Quantity(200.0, unitRef))

        val ui = item.toUi(resolver)

        assertEquals("Rice", ui.label)
        assertEquals("200 g", ui.quantity)
        assertEquals(true, ui.fromCatalogue)
    }

    @Test
    fun `a free-text line has no catalogue label`() {
        val item = item(freeText = "Toothpaste")

        val ui = item.toUi(LabelResolver())

        assertEquals("Toothpaste", ui.label)
        assertEquals(false, ui.fromCatalogue)
    }

    @Test
    fun `a catalogue miss falls back to the identifier`() {
        val item = item(ingredient = ingredientId)

        assertEquals(ingredientId.value, item.label(LabelResolver()))
    }

    @Test
    fun `a whole amount renders without a decimal point`() {
        val item = item(ingredient = ingredientId, quantity = Quantity(2.0, null))

        assertEquals("2", item.toUi(LabelResolver()).quantity)
    }

    private fun item(
        ingredient: IngredientId? = null,
        freeText: String? = null,
        quantity: Quantity? = null,
    ): ShoppingItem =
        ShoppingItem(
            id = ShoppingItemId.of("item-1").value(),
            ingredient = ingredient,
            freeText = freeText,
            quantity = quantity,
            checked = false,
            sourceRecipe = null,
            updatedAt = now,
        )

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
