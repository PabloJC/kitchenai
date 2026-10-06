package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.profile.ProfileAccountDelegate
import com.kitchenai.ui.presentation.profile.ProfileViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The profile screen. Every use case it asks for is bound by the `:shared` profile and session modules. */
val profilePresentationModule: Module =
    module {
        factory { ProfileAccountDelegate(get(), get(), get(), get()) }
        viewModel { ProfileViewModel(get(), get(), get(), get(), get(), get()) }
    }
