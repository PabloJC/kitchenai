package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.ClearCheckedItemsUseCase
import com.kitchenai.shared.domain.usecase.shopping.MoveCheckedItemsToPantryUseCase
import com.kitchenai.shared.domain.usecase.shopping.RemoveShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemCheckedUseCase

/**
 * What the shopping screen changes. Like [ShoppingReadsDelegate], it holds no logic and decides
 * nothing: it exists so the constructor names two roles instead of listing seven use cases.
 */
class ShoppingWritesDelegate(
    val add: AddShoppingItemUseCase,
    val setChecked: SetShoppingItemCheckedUseCase,
    val remove: RemoveShoppingItemUseCase,
    val clearChecked: ClearCheckedItemsUseCase,
    val moveCheckedToPantry: MoveCheckedItemsToPantryUseCase,
)
