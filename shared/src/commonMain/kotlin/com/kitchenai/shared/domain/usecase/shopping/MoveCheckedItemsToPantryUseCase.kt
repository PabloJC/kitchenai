package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.MoveToPantryOptions
import com.kitchenai.shared.domain.model.MovedToPantrySummary
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.PantryMoveRecord
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.UnitConverter
import com.kitchenai.shared.domain.usecase.pantry.draftPantryHolding
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import kotlin.time.Instant

/**
 * Moves what is ticked in the cart into the pantry — the same claim a checked line and a pantry
 * row both make, closed in one action instead of typed twice.
 *
 * A line with no stated amount stays on the list unless [MoveToPantryOptions.defaultAmountForBare]
 * says it moves as one. A date from [MoveToPantryOptions.expiresAt] that merges into a dated holding
 * keeps the earlier of the two, so the older food is used first.
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
    private val ingredients: IngredientRepositoryContract,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        options: MoveToPantryOptions = MoveToPantryOptions(),
    ): AppResult<MovedToPantrySummary> {
        val current = shoppingItems.getItems(kitchenId, listId)
        if (current is AppResult.Failure) return current
        val checked = (current as AppResult.Success).data.filter { it.checked }
        return sized(checked, options).flatMap { moving ->
            val skipped = checked.size - moving.size
            if (moving.isEmpty()) {
                AppResult.Success(MovedToPantrySummary(0, skipped))
            } else {
                when (val held = pantry.getPantry(kitchenId)) {
                    is AppResult.Failure -> held
                    is AppResult.Success -> commit(kitchenId, listId, held.data, moving, skipped, options.expiresAt)
                }
            }
        }
    }

    /** The lines that move, each with the amount it moves with. */
    private suspend fun sized(
        checked: List<ShoppingItem>,
        options: MoveToPantryOptions,
    ): AppResult<List<Pair<ShoppingItem, Quantity>>> {
        val moving = mutableListOf<Pair<ShoppingItem, Quantity>>()
        for (item in checked) {
            val quantity =
                item.quantity
                    ?: if (options.defaultAmountForBare) {
                        when (val unit = defaultUnitOf(item.ingredient)) {
                            is AppResult.Failure -> return unit
                            is AppResult.Success ->
                                Quantity(options.bareAmounts[item.id] ?: MoveToPantryOptions.BARE_AMOUNT, unit.data)
                        }
                    } else {
                        null
                    }
            if (quantity != null) moving += item to quantity
        }
        return AppResult.Success(moving)
    }

    /** An ingredient the catalogue no longer lists has no default unit: it moves as a plain count. */
    private suspend fun defaultUnitOf(ingredient: IngredientId?): AppResult<TermRef?> {
        if (ingredient == null) return AppResult.Success(null)
        return when (val found = ingredients.getIngredient(ingredient)) {
            is AppResult.Success -> AppResult.Success(found.data.defaultUnit)
            is AppResult.Failure -> if (found.error is AppError.NotFound) AppResult.Success(null) else found
        }
    }

    private suspend fun commit(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        held: List<PantryItem>,
        withQuantity: List<Pair<ShoppingItem, Quantity>>,
        skipped: Int,
        expiresAt: Map<ShoppingItemId, Instant>,
    ): AppResult<MovedToPantrySummary> =
        when (val converter = units()) {
            is AppResult.Failure -> converter
            is AppResult.Success ->
                when (val touched = plan(held, withQuantity, expiresAt, converter.data)) {
                    is AppResult.Failure -> touched
                    is AppResult.Success ->
                        pantry
                            .upsertAllConfirmed(kitchenId, touched.data)
                            .flatMap {
                                shoppingItems.removeItems(kitchenId, listId, withQuantity.map { (item, _) -> item.id })
                            }.map {
                                val record = record(listId, held, withQuantity.map { (item, _) -> item }, touched.data)
                                MovedToPantrySummary(withQuantity.size, skipped, record)
                            }
                }
        }

    /** Each touched row either existed in [held], and is put back as it was, or is new and goes. */
    private fun record(
        listId: ShoppingListId,
        held: List<PantryItem>,
        lines: List<ShoppingItem>,
        touched: List<PantryItem>,
    ): PantryMoveRecord {
        val touchedIds = touched.map { it.id }.toSet()
        val before = held.filter { it.id in touchedIds }
        val created = touchedIds - before.map { it.id }.toSet()
        return PantryMoveRecord(listId, lines, before, created.toList())
    }

    /**
     * Folded against a working copy, so two checked lines for the same ingredient in the same
     * unit, or units that convert, merge with each other too, not only with what the pantry held.
     */
    private fun plan(
        held: List<PantryItem>,
        lines: List<Pair<ShoppingItem, Quantity>>,
        expiresAt: Map<ShoppingItemId, Instant>,
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
                            expiresAt[item.id],
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
