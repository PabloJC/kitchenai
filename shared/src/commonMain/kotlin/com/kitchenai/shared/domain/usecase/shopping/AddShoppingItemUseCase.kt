package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider

/**
 * Adds a line, merging it into an existing one when it is the same ingredient in the same unit.
 *
 * The only non-idempotent operation on the list, which is why it deduplicates: adding flour
 * twice must leave one line, not two that a second device then has to reconcile.
 */
class AddShoppingItemUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
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
        val snapshot = shoppingItems.getItems(userId, listId)
        if (snapshot is AppResult.Failure) return snapshot
        val line = ShoppingLine(ingredient, freeText, quantity, sourceRecipe)
        val built = draftShoppingLine((snapshot as AppResult.Success).data, line, ids, time)
        if (built is AppResult.Failure) return built
        val item = (built as AppResult.Success).data
        return shoppingItems.upsertItems(userId, listId, listOf(item)).map { item }
    }
}
