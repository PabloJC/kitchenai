package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.shared.domain.usecase.recipe.CookRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.PlanCookUseCase
import com.kitchenai.shared.domain.usecase.recipe.RemoveSavedRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.SaveRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.UndoCookUseCase
import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.AddPantryItemsToShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase

/** What the detail screen changes. Like [RecipeDetailReadsDelegate], it holds no logic. */
class RecipeDetailWritesDelegate(
    val save: SaveRecipeUseCase,
    val unsave: RemoveSavedRecipeUseCase,
    /** A read, but only ever the first half of a cook: here so the cook and its preview sit together. */
    val planCook: PlanCookUseCase,
    val cook: CookRecipeUseCase,
    val undoCook: UndoCookUseCase,
    val addMissing: AddMissingIngredientsToShoppingListUseCase,
    val defaultList: EnsureDefaultShoppingListUseCase,
    val addToList: AddPantryItemsToShoppingListUseCase,
)
