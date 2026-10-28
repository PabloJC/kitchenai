package com.kitchenai.ui.presentation.session

import com.kitchenai.shared.domain.usecase.kitchen.EnsureKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.profile.ShareMyAvoidsUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase

/**
 * What the session asks of a kitchen: that one exists with its default list, to hear when it goes,
 * and that it holds this member's Avoid set.
 */
class SessionKitchenDelegate(
    val ensure: EnsureKitchenUseCase,
    val ensureDefaultList: EnsureDefaultShoppingListUseCase,
    val observe: ObserveKitchenUseCase,
    val shareAvoids: ShareMyAvoidsUseCase,
)
