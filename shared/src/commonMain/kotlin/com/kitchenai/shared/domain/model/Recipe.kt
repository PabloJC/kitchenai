package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult

/**
 * A dish the app can propose: what it needs, how it is made and where it came from.
 *
 * [title], [summary] and [steps] are content that arrives from the catalogue or from the
 * agent. Nothing in this codebase may write one of them as a literal.
 */
data class Recipe(
    val id: RecipeId,
    val title: String,
    val summary: String?,
    val servings: Int,
    val totalMinutes: Int?,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
    val tags: List<TermRef>,
    val source: RecipeSource,
)

/**
 * Rewrites the amounts for a different number of servings.
 *
 * Lines with no quantity and free-text lines pass through untouched: what "a pinch" scales to
 * is a guess, and a wrong amount reads as a fact.
 */
fun Recipe.scaledTo(servings: Int): AppResult<Recipe> {
    if (servings < 1 || this.servings < 1) {
        return AppResult.Failure(AppError.Validation("servings", "must be at least one"))
    }
    val factor = servings.toDouble() / this.servings.toDouble()
    return AppResult.Success(copy(servings = servings, ingredients = ingredients.map { it.scaledBy(factor) }))
}

private fun RecipeIngredient.scaledBy(factor: Double): RecipeIngredient =
    quantity?.let { copy(quantity = it.copy(amount = it.amount * factor)) } ?: this
