package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.onboarding.OnboardingReadsDelegate
import com.kitchenai.ui.presentation.onboarding.OnboardingViewModel
import com.kitchenai.ui.presentation.onboarding.OnboardingWritesDelegate
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The first run. Its use cases are bound by the `:shared` profile, kitchen and pantry modules. */
val onboardingPresentationModule: Module =
    module {
        factory { OnboardingReadsDelegate(get(), get(), get(), get()) }
        factory { OnboardingWritesDelegate(get(), get()) }
        viewModel { OnboardingViewModel(get(), get()) }
    }
