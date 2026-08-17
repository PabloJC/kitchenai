package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.ToggleDietaryConstraintUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Profile and vocabulary use cases. The ports they need are bound by the data issues. */
val profileModule: Module =
    module {
        factory { ObserveUserProfileUseCase(get()) }
        factory { ObserveTaxonomyUseCase(get()) }
        factory { ObserveTaxonomiesUseCase(get()) }
        factory { SaveUserProfileUseCase(get(), get(), get()) }
        factory { ToggleDietaryConstraintUseCase() }
    }
