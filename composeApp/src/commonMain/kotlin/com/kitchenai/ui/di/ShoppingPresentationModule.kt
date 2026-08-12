package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.shopping.ShoppingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The shopping screen's own bindings, so the shared presentation module grows by one line. */
val shoppingPresentationModule: Module =
    module {
        viewModel { ShoppingViewModel(get(), get(), get(), get(), get(), get(), get()) }
    }
