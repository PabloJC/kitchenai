package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.session.DeleteAccountUseCase
import com.kitchenai.shared.domain.usecase.session.EnsureSessionUseCase
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithAppleUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SignOutUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToAppleAccountUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToGoogleAccountUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

// The SessionRepositoryContract binding lives with its Firebase adapter, which is out of this issue's scope.
val sessionModule: Module =
    module {
        factory { ObserveSessionUseCase(get()) }
        factory { EnsureSessionUseCase(get()) }
        factory { SignInWithGoogleUseCase(get()) }
        factory { SwitchToGoogleAccountUseCase(get()) }
        factory { SignInWithAppleUseCase(get()) }
        factory { SwitchToAppleAccountUseCase(get()) }
        factory { SignOutUseCase(get()) }
        factory { DeleteAccountUseCase(get(), get()) }
    }
