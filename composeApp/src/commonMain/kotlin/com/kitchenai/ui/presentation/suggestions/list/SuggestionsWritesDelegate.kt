package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase

/** What the suggestions list changes. Like the detail screen's delegate, it holds no logic. */
class SuggestionsWritesDelegate(
    val addMissing: AddMissingIngredientsToShoppingListUseCase,
    val defaultList: EnsureDefaultShoppingListUseCase,
)
