package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.ShoppingListPort
import com.kitchenai.shared.domain.port.TimeProvider

/**
 * Adds a line, merging it into an existing one when it is the same ingredient in the same unit.
 *
 * The only non-idempotent operation on the list, which is why it deduplicates: adding flour
 * twice must leave one line, not two that a second device then has to reconcile.
 */
class AddShoppingItem(
    private val shoppingList: ShoppingListPort,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
        ingredient: IngredientId? = null,
        freeText: String? = null,
        quantity: Quantity? = null,
        sourceRecipe: RecipeId? = null,
    ): AppResult<ShoppingItem> {
        val snapshot = shoppingList.getItems(userId, listId)
        if (snapshot is AppResult.Failure) return snapshot
        val current = (snapshot as AppResult.Success).data
        val duplicate = ingredient?.let { known -> current.firstOrNull { it.absorbs(known, quantity) } }
        val built =
            if (duplicate == null) {
                create(ingredient, freeText, quantity, sourceRecipe)
            } else {
                merge(duplicate, quantity)
            }
        if (built is AppResult.Failure) return built
        val item = (built as AppResult.Success).data
        return shoppingList.upsertItems(userId, listId, listOf(item)).map { item }
    }

    // A free-text line never merges: two people write "the good bread" in two different ways,
    // and guessing they meant the same thing is worse than a duplicate line.
    private fun ShoppingItem.absorbs(
        other: IngredientId,
        added: Quantity?,
    ): Boolean {
        if (checked || ingredient != other) return false
        return when {
            quantity == null || added == null -> quantity == null && added == null
            else -> quantity.canCombineWith(added)
        }
    }

    private fun create(
        ingredient: IngredientId?,
        freeText: String?,
        quantity: Quantity?,
        sourceRecipe: RecipeId?,
    ): AppResult<ShoppingItem> =
        when (val id = ShoppingItemId.of(ids.newId())) {
            is AppResult.Failure -> id
            is AppResult.Success ->
                ShoppingItem.create(
                    id = id.data,
                    updatedAt = time.now(),
                    ingredient = ingredient,
                    freeText = freeText,
                    quantity = quantity,
                    sourceRecipe = sourceRecipe,
                )
        }

    private fun merge(
        existing: ShoppingItem,
        added: Quantity?,
    ): AppResult<ShoppingItem> {
        val total: AppResult<Quantity?> =
            if (existing.quantity == null || added == null) {
                AppResult.Success(existing.quantity)
            } else {
                existing.quantity + added
            }
        return total.map { existing.copy(quantity = it, updatedAt = time.now()) }
    }
}
