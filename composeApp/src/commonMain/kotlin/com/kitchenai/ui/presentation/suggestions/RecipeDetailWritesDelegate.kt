package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.domain.usecase.recipe.CookRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.SaveRecipeUseCase
import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase

/** What the detail screen changes. Like [RecipeDetailReadsDelegate], it holds no logic. */
class RecipeDetailWritesDelegate(
    val save: SaveRecipeUseCase,
    val cook: CookRecipeUseCase,
    val addMissing: AddMissingIngredientsToShoppingListUseCase,
    val defaultList: EnsureDefaultShoppingListUseCase,
)
