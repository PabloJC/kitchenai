package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.pantry.PantryReads
import com.kitchenai.ui.presentation.pantry.PantryViewModel
import com.kitchenai.ui.presentation.pantry.PantryWrites
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The pantry screen. Its use cases and ports are bound by the `:shared` modules. */
val pantryPresentationModule: Module =
    module {
        factory { PantryReads(get(), get(), get(), get()) }
        factory { PantryWrites(get(), get(), get(), get()) }
        viewModel { PantryViewModel(get(), get()) }
    }
