package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.session.EnsureSession
import com.kitchenai.shared.domain.usecase.session.ObserveSession
import com.kitchenai.shared.domain.usecase.session.SignOut
import org.koin.core.module.Module
import org.koin.dsl.module

// The SessionRepositoryContract binding lives with its Firebase adapter, which is out of this issue's scope.
val sessionModule: Module =
    module {
        factory { ObserveSession(get()) }
        factory { EnsureSession(get()) }
        factory { SignOut(get()) }
    }
