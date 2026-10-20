package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.onboarding.CompleteOnboardingUseCase
import com.kitchenai.shared.domain.usecase.onboarding.ObserveOnboardingCompletedUseCase
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveAppearanceUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveAppearanceUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.ToggleDietaryConstraintUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Profile, vocabulary and this device's own settings (appearance, first run). Ports come from the data modules. */
val profileModule: Module =
    module {
        factory { GetUnitConverterUseCase(get()) }
        factory { ObserveAppearanceUseCase(get()) }
        factory { SaveAppearanceUseCase(get()) }
        factory { ObserveOnboardingCompletedUseCase(get()) }
        factory { CompleteOnboardingUseCase(get()) }
        factory { ObserveUserProfileUseCase(get()) }
        factory { ObserveTaxonomyUseCase(get()) }
        factory { ObserveTaxonomiesUseCase(get()) }
        factory { SaveUserProfileUseCase(get(), get(), get(), get()) }
        factory { ToggleDietaryConstraintUseCase() }
    }
