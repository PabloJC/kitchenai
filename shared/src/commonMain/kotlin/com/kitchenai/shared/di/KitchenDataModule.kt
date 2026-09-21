package com.kitchenai.shared.di

import com.kitchenai.shared.data.repository.FirestoreKitchenRepository
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the kitchen port to its Firestore adapter. A singleton: it owns the error sinks its
 * observers read from, and a new instance per injection would strand them.
 */
val kitchenDataModule: Module =
    module {
        single<KitchenRepositoryContract> { FirestoreKitchenRepository(get(), get(), get(), get()) }
    }
