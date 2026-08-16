package com.kitchenai.shared.di

import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.local.KitchenAiDatabase
import com.kitchenai.shared.data.local.RecipeDao
import com.kitchenai.shared.data.local.RecipeLocalDataSource
import com.kitchenai.shared.data.local.open
import com.kitchenai.shared.data.local.recipeDatabaseBuilder
import com.kitchenai.shared.domain.port.LocalRecipePort
import org.koin.core.module.Module
import org.koin.dsl.module

/** The Room database is opened once, from whichever platform builder the current Koin scope resolves. */
val databaseModule: Module =
    module {
        single<KitchenAiDatabase> { recipeDatabaseBuilder().open(get<DispatcherProvider>().io) }
        single<RecipeDao> { get<KitchenAiDatabase>().recipeDao() }
        single<LocalRecipePort> { RecipeLocalDataSource(get(), get(), get()) }
    }
