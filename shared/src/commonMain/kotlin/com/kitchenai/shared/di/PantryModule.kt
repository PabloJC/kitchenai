package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.pantry.AddPantryItem
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItems
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.pantry.ObservePantry
import com.kitchenai.shared.domain.usecase.pantry.RemovePantryItem
import com.kitchenai.shared.domain.usecase.pantry.UpdatePantryItem
import org.koin.core.module.Module
import org.koin.dsl.module

/** The pantry use cases. The ports they ask for are bound by the data layer. */
val pantryModule: Module =
    module {
        factory { AddPantryItem(get(), get(), get()) }
        factory { ConsumePantryItems(get(), get()) }
        factory { ObserveIngredients(get()) }
        factory { ObservePantry(get()) }
        factory { RemovePantryItem(get()) }
        factory { UpdatePantryItem(get(), get()) }
    }
