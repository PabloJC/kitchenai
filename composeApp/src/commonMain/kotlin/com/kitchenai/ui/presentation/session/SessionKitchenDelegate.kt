package com.kitchenai.ui.presentation.session

import com.kitchenai.shared.domain.usecase.kitchen.EnsureKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase

/** What the session asks of a kitchen: that one exists with its default list, and to hear when it goes. */
class SessionKitchenDelegate(
    val ensure: EnsureKitchenUseCase,
    val ensureDefaultList: EnsureDefaultShoppingListUseCase,
    val observe: ObserveKitchenUseCase,
)
