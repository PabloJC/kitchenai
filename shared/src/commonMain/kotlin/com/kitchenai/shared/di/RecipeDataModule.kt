package com.kitchenai.shared.di

import com.kitchenai.shared.data.remote.firebase.RecipeRemoteDataSource
import com.kitchenai.shared.data.repository.RecipeRepository
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the recipe contract to a repository over both its data sources. A singleton: the remote
 * one owns the error sinks its observers read from, and a new instance per injection would
 * strand them.
 */
val recipeDataModule: Module =
    module {
        single { RecipeRemoteDataSource(get(), get(), get()) }
        single<RecipeRepositoryContract> { RecipeRepository(get(), get(), get(), get()) }
    }
