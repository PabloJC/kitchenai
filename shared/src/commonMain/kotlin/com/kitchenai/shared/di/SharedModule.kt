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
fun sharedModules(functionsRegion: String): List<Module> =
    listOf(
        agentDataModule(functionsRegion),
        agentModule,
        authModule,
        coreModule,
        crossFeatureModule,
        databaseModule,
        firebaseModule,
        pantryDataModule,
        pantryModule,
        profileDataModule,
        profileModule,
        recipeDataModule,
        recipeModule,
        sessionModule,
        shoppingDataModule,
        shoppingModule,
    )

/**
 * Single entry point; called by MainActivity (Android) and iOSApp (iOS).
 *
 * [functionsRegion] has no default. A wrong region fails as a call that reaches nothing rather
 * than as an error, so a default would be a value nobody chose behaving like one somebody did.
 */
fun initKoin(
    functionsRegion: String,
    appDeclaration: KoinAppDeclaration = {},
) = startKoin {
    appDeclaration()
    modules(sharedModules(functionsRegion))
}
