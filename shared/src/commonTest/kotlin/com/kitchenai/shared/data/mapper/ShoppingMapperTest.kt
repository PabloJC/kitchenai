package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.ShoppingItemDto
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.usecase.shopping.instant
import com.kitchenai.shared.domain.usecase.shopping.listId
import com.kitchenai.shared.domain.usecase.shopping.shoppingItem
import com.kitchenai.shared.domain.usecase.shopping.termRef
import com.kitchenai.shared.domain.usecase.shopping.userId
import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingMapperTest {
    @Test
    fun `a list round trips through the document shape`() {
        val list = ShoppingList(listId("list-1"), userId("user-1"), mapOf("en" to "label-1"), instant(1_000))

        val restored = list.toDto().toDomain(list.id.value)

        assertEquals(AppResult.Success(list), restored)
    }

    @Test
    fun `a ticked ingredient line round trips through the document shape`() {
        val quantity = Quantity(2.5, termRef("taxonomy-1", "term-1"))
        val item =
            shoppingItem("item-1", ingredient = "ingredient-1", quantity = quantity)
                .copy(checked = true, sourceRecipe = recipeId("recipe-1"))

        val restored = item.toDto().toDomain(item.id.value)

        assertEquals(AppResult.Success(item), restored)
    }

    @Test
    fun `a free text line round trips through the document shape`() {
        val item = shoppingItem("item-1", ingredient = null, freeText = "text-1")

        val dto = item.toDto()

        assertEquals(null, dto.ingredientId)
        assertEquals(null, dto.amount)
        assertEquals(AppResult.Success(item), dto.toDomain(item.id.value))
    }

    @Test
    fun `rejects a document that names an ingredient and a free text line at once`() {
        val mapped = shoppingItemDocument(freeText = "text-1").toDomain("item-1")

        assertEquals(AppResult.Failure(ONE_OF), mapped)
    }

    @Test
    fun `rejects a document that names neither an ingredient nor a free text line`() {
        val mapped = shoppingItemDocument(ingredientId = null).toDomain("item-1")

        assertEquals(AppResult.Failure(ONE_OF), mapped)
    }

    @Test
    fun `rejects a unit that names a taxonomy without its term`() {
        val mapped = shoppingItemDocument(amount = 1.0, unitTaxonomy = "taxonomy-1").toDomain("item-1")

        assertEquals(AppResult.Failure(AppError.Validation("unit", "incomplete term reference")), mapped)
    }

    @Test
    fun `rejects a unit carried without an amount`() {
        val mapped = shoppingItemDocument(unitTaxonomy = "taxonomy-1", unitTerm = "term-1").toDomain("item-1")

        assertEquals(AppResult.Failure(AppError.Validation("amount", "a unit without an amount")), mapped)
    }

    @Test
    fun `rejects a document whose id is blank`() {
        val mapped = shoppingItemDocument().toDomain(" ")

        assertEquals(AppResult.Failure(AppError.Validation("ShoppingItemId", "must not be blank")), mapped)
    }

    @Test
    fun `rejects a list document whose owner is blank`() {
        val list = ShoppingList(listId("list-1"), userId("user-1"), emptyMap(), instant(1_000))

        val mapped = list.toDto().copy(ownerId = "").toDomain("list-1")

        assertEquals(AppResult.Failure(AppError.Validation("UserId", "must not be blank")), mapped)
    }

    private companion object {
        // The invariant the domain factory rejects on; the mapper is expected to relay it verbatim.
        val ONE_OF = AppError.Validation("ingredient", "exactly one of ingredient or freeText must be set")
    }
}

/**
 * A line as Firestore stores it. Every identifier is opaque on purpose: naming a unit or an
 * ingredient in a fixture is the same mistake as naming it in code.
 */
internal fun shoppingItemDocument(
    ingredientId: String? = "ingredient-1",
    freeText: String? = null,
    amount: Double? = null,
    unitTaxonomy: String? = null,
    unitTerm: String? = null,
): ShoppingItemDto =
    ShoppingItemDto(
        ingredientId = ingredientId,
        freeText = freeText,
        amount = amount,
        unitTaxonomy = unitTaxonomy,
        unitTerm = unitTerm,
        updatedAtMillis = 1_000,
    )

private fun recipeId(raw: String): RecipeId = (RecipeId.of(raw) as AppResult.Success).data
