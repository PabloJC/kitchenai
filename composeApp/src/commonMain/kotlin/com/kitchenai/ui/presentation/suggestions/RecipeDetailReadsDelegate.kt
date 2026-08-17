package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetRecipeByIdUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetStoredRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantryUseCase

/**
 * What the detail screen reads: the dish, how it stands against the pantry, and the names for
 * its lines. Grouped so the constructor states two roles rather than listing seven use cases.
 */
class RecipeDetailReadsDelegate(
    val recipe: GetRecipeByIdUseCase,
    /** Checked before the repository: a generated dish is only ever here. */
    val storedRecipe: GetStoredRecipeUseCase,
    val match: MatchRecipeAgainstPantryUseCase,
    val ingredients: ObserveIngredientsUseCase,
    /** Units are terms, not ingredients: without these a quantity renders as a bare number. */
    val taxonomies: ObserveTaxonomiesUseCase,
    val taxonomy: ObserveTaxonomyUseCase,
)
