package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.pantry.AddPantryItemUseCase
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItemsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.pantry.RemovePantryItemUseCase
import com.kitchenai.shared.domain.usecase.pantry.UpdatePantryItemUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** The pantry use cases. The ports they ask for are bound by the data layer. */
val pantryModule: Module =
    module {
        factory { AddPantryItemUseCase(get(), get(), get()) }
        factory { ConsumePantryItemsUseCase(get(), get()) }
        factory { ObserveIngredientsUseCase(get()) }
        factory { ObservePantryUseCase(get()) }
        factory { RemovePantryItemUseCase(get()) }
        factory { UpdatePantryItemUseCase(get(), get()) }
    }
