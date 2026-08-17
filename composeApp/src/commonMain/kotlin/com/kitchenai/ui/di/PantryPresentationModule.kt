package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.pantry.PantryReadsDelegate
import com.kitchenai.ui.presentation.pantry.PantryViewModel
import com.kitchenai.ui.presentation.pantry.PantryWritesDelegate
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The pantry screen. Its use cases and ports are bound by the `:shared` modules. */
val pantryPresentationModule: Module =
    module {
        factory { PantryReadsDelegate(get(), get(), get(), get()) }
        factory { PantryWritesDelegate(get(), get(), get(), get()) }
        viewModel { PantryViewModel(get(), get()) }
    }
