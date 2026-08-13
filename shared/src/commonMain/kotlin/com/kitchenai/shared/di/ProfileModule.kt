package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomies
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomy
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfile
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfile
import com.kitchenai.shared.domain.usecase.profile.ToggleDietaryConstraint
import org.koin.core.module.Module
import org.koin.dsl.module

/** Profile and vocabulary use cases. The ports they need are bound by the data issues. */
val profileModule: Module =
    module {
        factory { ObserveUserProfile(get()) }
        factory { ObserveTaxonomy(get()) }
        factory { ObserveTaxonomies(get()) }
        factory { SaveUserProfile(get(), get(), get()) }
        factory { ToggleDietaryConstraint() }
    }
