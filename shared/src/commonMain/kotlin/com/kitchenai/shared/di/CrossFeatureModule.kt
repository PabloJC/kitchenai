package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.recipe.CookRecipeUseCase
import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.MoveCheckedItemsToPantryUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** The use cases that span two features, and so belong to neither feature module. */
val crossFeatureModule: Module =
    module {
        factory { AddMissingIngredientsToShoppingListUseCase(get(), get(), get(), get(), get()) }
        factory { CookRecipeUseCase(get(), get(), get(), get()) }
        factory { MoveCheckedItemsToPantryUseCase(get(), get(), get(), get()) }
    }
