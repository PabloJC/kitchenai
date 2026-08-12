package com.kitchenai.shared.domain.model

/**
 * What a pantry does and does not cover of one recipe.
 *
 * [unverifiable] is the honest third answer: a free-text line and an amount in a unit nothing
 * converts are neither covered nor missing, and the MVP does not pick one.
 */
data class PantryMatch(
    val recipeId: RecipeId,
    val covered: List<CoveredIngredient>,
    val missing: List<MissingIngredient>,
    val unverifiable: List<RecipeIngredient>,
) {
    /**
     * Share of the lines that must be there and are, 0f when nothing can be counted.
     * Optional lines are reported but excluded: a recipe is not half missing over a garnish.
     */
    val coverage: Float
        get() {
            val held = covered.count { !it.ingredient.optional }
            val total = held + missing.count { !it.ingredient.optional }
            return if (total == 0) 0f else held.toFloat() / total.toFloat()
        }
}

/** A line the pantry satisfies, and the holdings that satisfy it. */
data class CoveredIngredient(
    val ingredient: RecipeIngredient,
    val heldBy: List<PantryItemId>,
)

/** A line the pantry does not satisfy, with the shortfall when the amounts are comparable. */
data class MissingIngredient(
    val ingredient: RecipeIngredient,
    val shortfall: Quantity?,
)
