package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.profile.ProfileViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The profile screen. Every use case it asks for is bound by the `:shared` profile module. */
val profilePresentationModule: Module =
    module {
        viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }
    }
