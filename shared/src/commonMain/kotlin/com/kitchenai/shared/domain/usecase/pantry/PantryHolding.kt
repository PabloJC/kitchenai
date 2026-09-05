package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.port.IdGenerator
import kotlin.time.Instant

/**
 * The holding to write for one line, given [held] as a snapshot: an existing row topped up, or
 * a new one. Shared so that adding a single item and folding a batch of lines against a working
 * copy deduplicate by the very same rule.
 *
 * A free-text holding never merges: two different spellings of "the good bread" are not provably
 * the same thing, and folding them into one row risks discarding a real one.
 */
@Suppress("LongParameterList")
internal fun draftPantryHolding(
    held: List<PantryItem>,
    ingredient: IngredientId?,
    freeText: String?,
    quantity: Quantity,
    location: TermRef?,
    expiresAt: Instant?,
    ids: IdGenerator,
    now: Instant,
): AppResult<PantryItem> {
    // Enforced here rather than left to each caller: AddPantryItemUseCase used to check this
    // itself before this logic was shared, and a caller that forgot would silently let a
    // zero or negative amount through the merge path, which builds a PantryItem directly and
    // never touches PantryItem.create's own checks either.
    if (quantity.amount <= 0.0) {
        return AppResult.Failure(AppError.Validation("amount", "must be greater than zero"))
    }
    val mergeInto =
        ingredient?.let { known -> held.firstOrNull { it.ingredient == known && it.quantity.canCombineWith(quantity) } }
    return mergeInto?.let { existing ->
        (existing.quantity + quantity).map { total ->
            existing.copy(
                quantity = total,
                location = location ?: existing.location,
                // A merged pile is only as good as its soonest expiry.
                expiresAt = listOfNotNull(existing.expiresAt, expiresAt).minOrNull(),
                updatedAt = now,
            )
        }
    } ?: PantryItemId.of(ids.newId()).flatMap { id ->
        PantryItem.create(id, quantity, now, ingredient, freeText, location, expiresAt)
    }
}
