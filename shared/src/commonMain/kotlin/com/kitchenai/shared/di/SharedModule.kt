package com.kitchenai.shared.di

import com.kitchenai.shared.core.DefaultDispatcherProvider
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.remote.firebase.FirebaseHealthCheckAdapter
import com.kitchenai.shared.domain.port.HealthCheckPort
import com.kitchenai.shared.domain.usecase.CheckFirebaseHealth
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
        // The binding is to the port, not to the class: that is what stops a use case
        // from asking for the adapter and dragging Firebase into the domain.
        factory<HealthCheckPort> { FirebaseHealthCheckAdapter(get()) }
    }

val domainModule: Module =
    module {
        factory { CheckFirebaseHealth(get()) }
    }

// One module per line and alphabetical: several issues append to this list in parallel.
val sharedModules: List<Module> =
    listOf(
        coreModule,
        dataModule,
        domainModule,
        firebaseModule,
        pantryDataModule,
        pantryModule,
        profileModule,
        sessionModule,
        shoppingModule,
    )

/** Single entry point; called by MainActivity (Android) and iOSApp (iOS). */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(sharedModules)
    }
