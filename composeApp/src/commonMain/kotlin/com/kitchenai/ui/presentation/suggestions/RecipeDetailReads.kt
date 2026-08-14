package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.recipe.GetRecipeById
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantry

/**
 * What the detail screen reads: the dish, how it stands against the pantry, and the names for
 * its lines. Grouped so the constructor states two roles rather than listing seven use cases.
 */
class RecipeDetailReads(
    val recipe: GetRecipeById,
    val match: MatchRecipeAgainstPantry,
    val ingredients: ObserveIngredients,
)
