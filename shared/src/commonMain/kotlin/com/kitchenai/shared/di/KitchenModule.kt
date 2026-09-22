package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.kitchen.EnsureKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.JoinKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.LeaveKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RegenerateKitchenJoinCodeUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RemoveKitchenMemberUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** The kitchen use cases. The port binding comes with the Firestore adapter. */
val kitchenModule: Module =
    module {
        factory { EnsureKitchenUseCase(get()) }
        factory { JoinKitchenUseCase(get(), get()) }
        factory { LeaveKitchenUseCase(get()) }
        factory { ObserveKitchenUseCase(get()) }
        factory { RegenerateKitchenJoinCodeUseCase(get()) }
        factory { RemoveKitchenMemberUseCase(get()) }
    }
