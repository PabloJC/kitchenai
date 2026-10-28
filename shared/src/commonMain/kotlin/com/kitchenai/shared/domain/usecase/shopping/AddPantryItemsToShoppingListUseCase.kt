package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem

/**
 * Puts pantry rows back on the default list, as bare lines: something run out is bought again,
 * but how much is the shopper's call, not what the pantry last held.
 */
class AddPantryItemsToShoppingListUseCase(
    private val defaultList: EnsureDefaultShoppingListUseCase,
    private val add: AddShoppingItemUseCase,
) {
    /** [listLabels] names the list only if this creates it. Answers how many lines were added. */
    suspend operator fun invoke(
        kitchenId: KitchenId,
        listLabels: Map<String, String>,
        items: List<PantryItem>,
    ): AppResult<Int> {
        if (items.isEmpty()) return AppResult.Success(0)
        val list = defaultList(kitchenId, listLabels)
        if (list is AppResult.Failure) return list
        val listId = (list as AppResult.Success).data
        for (item in items) {
            val added = add(kitchenId, listId, ingredient = item.ingredient, freeText = item.freeText)
            if (added is AppResult.Failure) return added
        }
        return AppResult.Success(items.size)
    }
}
