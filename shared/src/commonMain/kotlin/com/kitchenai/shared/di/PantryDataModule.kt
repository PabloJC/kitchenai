package com.kitchenai.shared.di

import com.kitchenai.shared.data.repository.FirestoreIngredientRepository
import com.kitchenai.shared.data.repository.FirestorePantryRepository
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the pantry ports to their Firestore adapters. Singletons: each one owns the error sinks
 * its observers read from, and a new instance per injection would strand them.
 */
val pantryDataModule: Module =
    module {
        single<PantryRepositoryContract> { FirestorePantryRepository(get(), get(), get()) }
        single<IngredientRepositoryContract> { FirestoreIngredientRepository(get(), get()) }
    }
