package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase

/**
 * Adds a line, merging it into an existing one when it is the same ingredient in the same unit,
 * or in a unit that converts into it.
 *
 * The only non-idempotent operation on the list, which is why it deduplicates: adding flour
 * twice must leave one line, not two that a second device then has to reconcile.
 */
class AddShoppingItemUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
    private val units: GetUnitConverterUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        ingredient: IngredientId? = null,
        freeText: String? = null,
        quantity: Quantity? = null,
        sourceRecipe: RecipeId? = null,
    ): AppResult<ShoppingItem> {
        val snapshot = shoppingItems.getItems(kitchenId, listId)
        if (snapshot is AppResult.Failure) return snapshot
        val converter = units()
        if (converter is AppResult.Failure) return converter
        val line = ShoppingLine(ingredient, freeText, quantity, sourceRecipe)
        val built =
            draftShoppingLine(
                (snapshot as AppResult.Success).data,
                line,
                ids,
                time,
                (converter as AppResult.Success).data,
            )
        if (built is AppResult.Failure) return built
        val item = (built as AppResult.Success).data
        return shoppingItems.upsertItems(kitchenId, listId, listOf(item)).map { item }
    }
}
