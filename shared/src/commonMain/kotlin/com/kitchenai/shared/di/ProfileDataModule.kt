package com.kitchenai.shared.di

import com.kitchenai.shared.data.repository.FirestoreTaxonomyRepository
import com.kitchenai.shared.data.repository.FirestoreUserProfileRepository
import com.kitchenai.shared.domain.port.TaxonomyPort
import com.kitchenai.shared.domain.port.UserProfilePort
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Firestore side of the profile and the vocabulary catalogue.
 *
 * `single`, not `factory`: each repository owns the error streams its observers report on, so a
 * second instance would hand a collector a stream nobody publishes to.
 */
val profileDataModule: Module =
    module {
        single<UserProfilePort> { FirestoreUserProfileRepository(get(), get()) }
        single<TaxonomyPort> { FirestoreTaxonomyRepository(get(), get()) }
    }
