package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult

/**
 * One line of a recipe: either a catalogue [ingredient] or a [freeText] line, never both.
 *
 * Only a catalogue line can be compared with the pantry; free text is reported as unverifiable
 * rather than resolved by guesswork.
 */
data class RecipeIngredient(
    val ingredient: IngredientId?,
    val freeText: String?,
    val quantity: Quantity?,
    val optional: Boolean,
) {
    companion object {
        /**
         * Builds a line, rejecting the both-null and both-non-null cases.
         *
         * The invariant lives here and not in `init`, because a thrown exception would cross a
         * layer boundary.
         */
        fun create(
            ingredient: IngredientId? = null,
            freeText: String? = null,
            quantity: Quantity? = null,
            optional: Boolean = false,
        ): AppResult<RecipeIngredient> {
            val text = freeText?.takeIf { it.isNotBlank() }
            if ((ingredient == null) == (text == null)) {
                return AppResult.Failure(
                    AppError.Validation("ingredient", "exactly one of ingredient or freeText must be set"),
                )
            }
            return AppResult.Success(RecipeIngredient(ingredient, text, quantity, optional))
        }
    }
}
