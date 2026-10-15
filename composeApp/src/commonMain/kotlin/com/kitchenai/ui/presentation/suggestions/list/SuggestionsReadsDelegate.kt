package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.recipe.ObserveSavedRecipesUseCase

/** What the suggestions list listens to. Like the detail screen's delegate, it holds no logic. */
class SuggestionsReadsDelegate(
    val ingredients: ObserveIngredientsUseCase,
    val savedRecipes: ObserveSavedRecipesUseCase,
    val pantry: ObservePantryUseCase,
    val kitchen: ObserveKitchenUseCase,
)
