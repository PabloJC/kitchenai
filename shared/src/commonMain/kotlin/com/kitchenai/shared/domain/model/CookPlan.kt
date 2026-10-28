package com.kitchenai.shared.domain.model

/**
 * What cooking a recipe would do to the pantry, worked out before anything is written: so it can
 * be shown, confirmed, applied as shown, and undone.
 */
data class CookPlan(
    val deductions: List<PantryDeduction>,
    /** Lines the pantry does not cover; they are never consumed, even by a cook that goes ahead. */
    val missing: List<MissingIngredient>,
) {
    val isComplete: Boolean get() = missing.none { !it.ingredient.optional }

    /** Rows this cook empties, as they were before it: what there is to buy again. */
    val usedUp: List<PantryItem> get() = deductions.filter { it.after.quantity.amount <= 0.0 }.map { it.before }
}
