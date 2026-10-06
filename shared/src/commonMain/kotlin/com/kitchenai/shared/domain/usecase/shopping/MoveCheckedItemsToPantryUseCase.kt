package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.MovedToPantrySummary
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.UnitConverter
import com.kitchenai.shared.domain.usecase.pantry.draftPantryHolding
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase

/**
 * Moves what is ticked in the cart into the pantry — the same claim a checked line and a pantry
 * row both make, closed in one action instead of typed twice.
 *
 * A line with no stated amount stays on the list rather than inventing one: "milk" with no
 * quantity is a normal shopping line and not a normal pantry row.
 *
 * Every touched holding is folded against one pantry snapshot before anything is written, then
 * committed with a single [PantryRepositoryContract.upsertAllConfirmed] rather than
 * [PantryRepositoryContract.upsertAll]: the ordinary optimistic writes this app uses everywhere
 * else return before the server has answered, so a plain `upsertAll` here would always "succeed"
 * and the list lines would be removed regardless of whether the pantry write actually landed —
 * losing the item outright rather than merely under-applying it. Waiting for the real answer is
 * what lets [invoke] refuse to call [ShoppingItemRepositoryContract.removeItems] on a pantry
 * write that never happened. The removal itself stays optimistic, like every other list edit in
 * this app: if it alone fails to reach the server, the line reappears once the listener catches
 * up, which is a stale row, not a lost one.
 */
class MoveCheckedItemsToPantryUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
    private val units: GetUnitConverterUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): AppResult<MovedToPantrySummary> {
        val current = shoppingItems.getItems(kitchenId, listId)
        if (current is AppResult.Failure) return current
        val checked = (current as AppResult.Success).data.filter { it.checked }
        val withQuantity = checked.mapNotNull { item -> item.quantity?.let { quantity -> item to quantity } }
        val skipped = checked.size - withQuantity.size
        if (withQuantity.isEmpty()) return AppResult.Success(MovedToPantrySummary(0, skipped))
        val held = pantry.getPantry(kitchenId)
        if (held is AppResult.Failure) return held
        return commit(kitchenId, listId, (held as AppResult.Success).data, withQuantity, skipped)
    }

    private suspend fun commit(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        held: List<PantryItem>,
        withQuantity: List<Pair<ShoppingItem, Quantity>>,
        skipped: Int,
    ): AppResult<MovedToPantrySummary> =
        when (val converter = units()) {
            is AppResult.Failure -> converter
            is AppResult.Success ->
                when (val touched = plan(held, withQuantity, converter.data)) {
                    is AppResult.Failure -> touched
                    is AppResult.Success ->
                        pantry
                            .upsertAllConfirmed(kitchenId, touched.data)
                            .flatMap {
                                shoppingItems.removeItems(kitchenId, listId, withQuantity.map { (item, _) -> item.id })
                            }.map { MovedToPantrySummary(withQuantity.size, skipped) }
                }
        }

    /**
     * Folded against a working copy, so two checked lines for the same ingredient in the same
     * unit, or units that convert, merge with each other too, not only with what the pantry held.
     */
    private fun plan(
        held: List<PantryItem>,
        lines: List<Pair<ShoppingItem, Quantity>>,
        converter: UnitConverter,
    ): AppResult<List<PantryItem>> {
        val working = held.toMutableList()
        val touched = LinkedHashMap<PantryItemId, PantryItem>()
        val now = time.now()
        for ((item, quantity) in lines) {
            val built =
                when (
                    val drafted =
                        draftPantryHolding(
                            working,
                            item.ingredient,
                            item.freeText,
                            quantity,
                            null,
                            null,
                            ids,
                            now,
                            converter,
                        )
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
