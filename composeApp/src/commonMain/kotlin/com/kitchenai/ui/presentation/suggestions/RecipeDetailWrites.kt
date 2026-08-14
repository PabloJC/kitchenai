package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.domain.usecase.recipe.CookRecipe
import com.kitchenai.shared.domain.usecase.recipe.SaveRecipe
import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingList
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingList

/** What the detail screen changes. Like [RecipeDetailReads], it holds no logic. */
class RecipeDetailWrites(
    val save: SaveRecipe,
    val cook: CookRecipe,
    val addMissing: AddMissingIngredientsToShoppingList,
    val defaultList: EnsureDefaultShoppingList,
)
