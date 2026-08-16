package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomies
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomy
import com.kitchenai.shared.domain.usecase.recipe.GetRecipeById
import com.kitchenai.shared.domain.usecase.recipe.GetStoredRecipe
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantry

/**
 * What the detail screen reads: the dish, how it stands against the pantry, and the names for
 * its lines. Grouped so the constructor states two roles rather than listing seven use cases.
 */
class RecipeDetailReads(
    val recipe: GetRecipeById,
    /** Checked before the repository: a generated dish is only ever here. */
    val storedRecipe: GetStoredRecipe,
    val match: MatchRecipeAgainstPantry,
    val ingredients: ObserveIngredients,
    /** Units are terms, not ingredients: without these a quantity renders as a bare number. */
    val taxonomies: ObserveTaxonomies,
    val taxonomy: ObserveTaxonomy,
)
