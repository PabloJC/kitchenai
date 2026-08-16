package com.kitchenai.shared.di

import com.kitchenai.shared.data.repository.FirestoreShoppingItemRepository
import com.kitchenai.shared.data.repository.FirestoreShoppingListRepository
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingListRepositoryContract
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the two shopping ports to their Firestore adapters. Singletons: each one owns the error
 * sinks its observers read from, and a new instance per injection would strand them.
 */
val shoppingDataModule: Module =
    module {
        single<ShoppingListRepositoryContract> { FirestoreShoppingListRepository(get(), get()) }
        single<ShoppingItemRepositoryContract> { FirestoreShoppingItemRepository(get(), get(), get()) }
    }
