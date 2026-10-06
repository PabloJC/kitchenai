package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.kitchen.KitchenViewModel
import com.kitchenai.ui.presentation.kitchen.KitchenWritesDelegate
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The kitchen-membership screen. Every use case it asks for is bound by the `:shared` kitchen module. */
val kitchenPresentationModule: Module =
    module {
        factory { KitchenWritesDelegate(get(), get(), get(), get()) }
        viewModel { KitchenViewModel(get(), get()) }
    }
