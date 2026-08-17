package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.ClearCheckedItemsUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.ObserveShoppingItemsUseCase
import com.kitchenai.shared.domain.usecase.shopping.RemoveShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemCheckedUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Shopping use cases. Both port bindings come with the Firestore adapter. */
val shoppingModule: Module =
    module {
        factory { AddShoppingItemUseCase(get(), get(), get()) }
        factory { ClearCheckedItemsUseCase(get()) }
        factory { EnsureDefaultShoppingListUseCase(get(), get(), get()) }
        factory { ObserveShoppingItemsUseCase(get()) }
        factory { RemoveShoppingItemUseCase(get()) }
        factory { SetShoppingItemCheckedUseCase(get(), get()) }
    }
