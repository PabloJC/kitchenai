package com.kitchenai.ui.di

import com.kitchenai.shared.di.initKoin
import com.kitchenai.ui.presentation.health.HealthViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val presentationModule: Module =
    module {
        viewModel { HealthViewModel(get()) }
    }

/**
 * Starts Koin with the `:shared` modules plus presentation.
 *
 * `initKoin` lives in `:shared`, which cannot see `:composeApp`, so both entry points call
 * this instead.
 */
fun initKoinUi(appDeclaration: KoinAppDeclaration = {}) =
    initKoin {
        appDeclaration()
        modules(presentationModule)
    }
