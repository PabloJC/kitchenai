package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.MovedToPantrySummary
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.draftPantryHolding

/**
 * Moves what is ticked in the cart into the pantry — the same claim a checked line and a pantry
 * row both make, closed in one action instead of typed twice.
 *
 * A line with no stated amount stays on the list rather than inventing one: "milk" with no
 * quantity is a normal shopping line and not a normal pantry row.
 *
 * Every touched holding is folded against one pantry snapshot before anything is written, then
 * committed with a single [PantryRepositoryContract.upsertAll] — the same shape
 * [com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItemsUseCase] uses for
 * `CookRecipeUseCase`. Writing one line at a time would leave an earlier line already merged
 * into the pantry if a later one failed, and since a failed move leaves every line checked, the
 * natural retry would merge those same lines again.
 */
class MoveCheckedItemsToPantryUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<MovedToPantrySummary> {
        val current = shoppingItems.getItems(userId, listId)
        if (current is AppResult.Failure) return current
        val checked = (current as AppResult.Success).data.filter { it.checked }
        val withQuantity = checked.mapNotNull { item -> item.quantity?.let { quantity -> item to quantity } }
        val skipped = checked.size - withQuantity.size
        if (withQuantity.isEmpty()) return AppResult.Success(MovedToPantrySummary(0, skipped))
        val held = pantry.getPantry(userId)
        if (held is AppResult.Failure) return held
        return when (val touched = plan((held as AppResult.Success).data, withQuantity)) {
            is AppResult.Failure -> touched
            is AppResult.Success ->
                pantry
                    .upsertAll(userId, touched.data)
                    .flatMap {
                        shoppingItems.removeItems(userId, listId, withQuantity.map { (item, _) -> item.id })
                    }.map { MovedToPantrySummary(withQuantity.size, skipped) }
        }
    }

    /**
     * Folded against a working copy, so two checked lines for the same ingredient in the same
     * unit merge with each other too, not only with what the pantry already held.
     */
    private fun plan(
        held: List<PantryItem>,
        lines: List<Pair<ShoppingItem, Quantity>>,
    ): AppResult<List<PantryItem>> {
        val working = held.toMutableList()
        val touched = LinkedHashMap<PantryItemId, PantryItem>()
        val now = time.now()
        for ((item, quantity) in lines) {
            val built =
                when (
                    val drafted =
                        draftPantryHolding(working, item.ingredient, item.freeText, quantity, null, null, ids, now)
                ) {
                    is AppResult.Failure -> return drafted
                    is AppResult.Success -> drafted.data
                }
            working.removeAll { it.id == built.id }
            working += built
            touched[built.id] = built
        }
        return AppResult.Success(touched.values.toList())
    }
}
