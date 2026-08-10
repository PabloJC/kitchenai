package com.kitchenai.shared.di

import com.kitchenai.shared.core.DefaultDispatcherProvider
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.remote.firebase.FirebaseHealthCheck
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val coreModule: Module =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider() }
    }

val dataModule: Module =
    module {
        factory { FirebaseHealthCheck() }
    }

val domainModule: Module =
    module {
        // Los casos de uso se registran aquí conforme se van creando.
    }

val sharedModules: List<Module> = listOf(coreModule, dataModule, domainModule)

/** Punto de entrada único; lo llaman MainActivity (Android) y iOSApp (iOS). */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(sharedModules)
    }
