package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItem
import com.kitchenai.shared.domain.usecase.shopping.ClearCheckedItems
import com.kitchenai.shared.domain.usecase.shopping.RemoveShoppingItem
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemChecked

/**
 * What the shopping screen changes. Like [ShoppingReads], it holds no logic and decides
 * nothing: it exists so the constructor names two roles instead of listing seven use cases.
 */
class ShoppingWrites(
    val add: AddShoppingItem,
    val setChecked: SetShoppingItemChecked,
    val remove: RemoveShoppingItem,
    val clearChecked: ClearCheckedItems,
)
