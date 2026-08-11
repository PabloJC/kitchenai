package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlin.time.Instant

/**
 * One line of a shopping list: either a catalogue [ingredient] or a [freeText] line, never both.
 *
 * Every mutation is an idempotent assignment over the whole document rather than a delta, so
 * two devices editing the same line converge under last-write-wins with nothing to merge.
 */
data class ShoppingItem(
    val id: ShoppingItemId,
    val ingredient: IngredientId?,
    val freeText: String?,
    val quantity: Quantity?,
    val checked: Boolean,
    val sourceRecipe: RecipeId?,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * Builds an unchecked line, rejecting the both-null and both-non-null cases.
         *
         * The invariant lives here and not in `init`, because a thrown exception would cross a
         * layer boundary. Checking a line afterwards is a `copy`, not another factory.
         */
        fun create(
            id: ShoppingItemId,
            updatedAt: Instant,
            ingredient: IngredientId? = null,
            freeText: String? = null,
            quantity: Quantity? = null,
            sourceRecipe: RecipeId? = null,
        ): AppResult<ShoppingItem> {
            val text = freeText?.takeIf { it.isNotBlank() }
            if ((ingredient == null) == (text == null)) {
                return AppResult.Failure(
                    AppError.Validation("ingredient", "exactly one of ingredient or freeText must be set"),
                )
            }
            return AppResult.Success(ShoppingItem(id, ingredient, text, quantity, false, sourceRecipe, updatedAt))
        }
    }
}
