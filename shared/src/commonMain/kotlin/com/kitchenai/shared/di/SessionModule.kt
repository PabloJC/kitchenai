package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.session.EnsureSessionUseCase
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.session.SignOutUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

// The SessionRepositoryContract binding lives with its Firebase adapter, which is out of this issue's scope.
val sessionModule: Module =
    module {
        factory { ObserveSessionUseCase(get()) }
        factory { EnsureSessionUseCase(get()) }
        factory { SignOutUseCase(get()) }
    }
