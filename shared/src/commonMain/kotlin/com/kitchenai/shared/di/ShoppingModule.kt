package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItem
import com.kitchenai.shared.domain.usecase.shopping.ClearCheckedItems
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingList
import com.kitchenai.shared.domain.usecase.shopping.ObserveShoppingItems
import com.kitchenai.shared.domain.usecase.shopping.RemoveShoppingItem
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemChecked
import org.koin.core.module.Module
import org.koin.dsl.module

/** Shopping list use cases. The `ShoppingListPort` binding comes with the Firestore adapter. */
val shoppingModule: Module =
    module {
        factory { AddShoppingItem(get(), get(), get()) }
        factory { ClearCheckedItems(get()) }
        factory { EnsureDefaultShoppingList(get(), get(), get()) }
        factory { ObserveShoppingItems(get()) }
        factory { RemoveShoppingItem(get()) }
        factory { SetShoppingItemChecked(get(), get()) }
    }
