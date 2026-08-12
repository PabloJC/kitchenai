package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import kotlin.time.Instant

/** One requested addition, before it is known whether it opens a line or folds into one. */
internal data class ShoppingLine(
    val ingredient: IngredientId?,
    val freeText: String?,
    val quantity: Quantity?,
    val sourceRecipe: RecipeId?,
)

/**
 * The line to write for [line] given the list as [current] holds it: an existing line topped
 * up, or a new one.
 *
 * Shared rather than duplicated so that adding one item and adding a whole recipe deduplicate
 * by the very same rule.
 */
internal fun draftShoppingLine(
    current: List<ShoppingItem>,
    line: ShoppingLine,
    ids: IdGenerator,
    time: TimeProvider,
): AppResult<ShoppingItem> {
    val duplicate = line.ingredient?.let { known -> current.firstOrNull { it.absorbs(known, line.quantity) } }
    return duplicate?.mergedWith(line.quantity, time.now()) ?: line.open(ids, time)
}

// A free-text line never merges: two people write "the good bread" in two different ways, and
// guessing they meant the same thing is worse than a duplicate line.
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

private fun ShoppingItem.mergedWith(
    added: Quantity?,
    now: Instant,
): AppResult<ShoppingItem> {
    val total: AppResult<Quantity?> =
        if (quantity == null || added == null) AppResult.Success(quantity) else quantity + added
    return total.map { copy(quantity = it, updatedAt = now) }
}

private fun ShoppingLine.open(
    ids: IdGenerator,
    time: TimeProvider,
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
