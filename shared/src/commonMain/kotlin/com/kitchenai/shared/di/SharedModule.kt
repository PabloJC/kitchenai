package com.kitchenai.shared.di

import com.kitchenai.shared.core.DefaultDispatcherProvider
import com.kitchenai.shared.core.DispatcherProvider
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val coreModule: Module =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider() }
    }

// One module per line and alphabetical: several issues append to this list in parallel.
val sharedModules: List<Module> =
    listOf(
        agentModule,
        authModule,
        coreModule,
        crossFeatureModule,
        firebaseModule,
        pantryDataModule,
        pantryModule,
        profileDataModule,
        profileModule,
        recipeModule,
        sessionModule,
        shoppingDataModule,
        shoppingModule,
    )

/** Single entry point; called by MainActivity (Android) and iOSApp (iOS). */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(sharedModules)
    }
