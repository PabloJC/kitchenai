package com.kitchenai.shared.di

import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.local.KitchenAiDatabase
import com.kitchenai.shared.data.local.RecipeDao
import com.kitchenai.shared.data.local.RecipeLocalDataSource
import com.kitchenai.shared.data.local.open
import com.kitchenai.shared.data.local.recipeDatabaseBuilder
import com.kitchenai.shared.data.repository.RecipeRepository
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import org.koin.core.module.Module
import org.koin.dsl.module

/** The Room database is opened once, from whichever platform builder the current Koin scope resolves. */
val databaseModule: Module =
    module {
        single<KitchenAiDatabase> { recipeDatabaseBuilder().open(get<DispatcherProvider>().io) }
        single<RecipeDao> { get<KitchenAiDatabase>().recipeDao() }
        single { RecipeLocalDataSource(get()) }
        single<RecipeRepositoryContract> { RecipeRepository(get(), get(), get()) }
    }
