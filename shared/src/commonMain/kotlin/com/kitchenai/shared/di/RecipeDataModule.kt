package com.kitchenai.shared.di

import com.kitchenai.shared.data.repository.FirestoreRecipeRepository
import com.kitchenai.shared.domain.port.RecipePort
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the recipe port to its Firestore adapter. A singleton: it owns the error sinks its
 * observers read from, and a new instance per injection would strand them.
 */
val recipeDataModule: Module =
    module {
        single<RecipePort> { FirestoreRecipeRepository(get(), get(), get(), get()) }
    }
