package com.kitchenai.shared.di

import com.kitchenai.shared.data.local.AppearanceLocalDataSource
import com.kitchenai.shared.data.local.OnboardingLocalDataSource
import com.kitchenai.shared.data.local.ReminderSettingsLocalDataSource
import com.kitchenai.shared.data.local.platformPreferenceStore
import com.kitchenai.shared.data.repository.AppearanceRepository
import com.kitchenai.shared.data.repository.FirestoreTaxonomyRepository
import com.kitchenai.shared.data.repository.FirestoreUserProfileRepository
import com.kitchenai.shared.data.repository.OnboardingRepository
import com.kitchenai.shared.data.repository.ReminderSettingsRepository
import com.kitchenai.shared.domain.port.AppearanceRepositoryContract
import com.kitchenai.shared.domain.port.OnboardingRepositoryContract
import com.kitchenai.shared.domain.port.ReminderSettingsRepositoryContract
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
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
        single<UserProfileRepositoryContract> { FirestoreUserProfileRepository(get(), get()) }
        single<TaxonomyRepositoryContract> { FirestoreTaxonomyRepository(get(), get()) }
        single { AppearanceLocalDataSource(platformPreferenceStore()) }
        single<AppearanceRepositoryContract> { AppearanceRepository(get(), get()) }
        single { OnboardingLocalDataSource(platformPreferenceStore()) }
        single<OnboardingRepositoryContract> { OnboardingRepository(get(), get()) }
        single { ReminderSettingsLocalDataSource(platformPreferenceStore()) }
        single<ReminderSettingsRepositoryContract> { ReminderSettingsRepository(get(), get()) }
    }
