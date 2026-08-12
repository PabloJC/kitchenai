package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.recipe.CookRecipe
import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingList
import org.koin.core.module.Module
import org.koin.dsl.module

/** The use cases that span two features, and so belong to neither feature module. */
val crossFeatureModule: Module =
    module {
        factory { AddMissingIngredientsToShoppingList(get(), get(), get(), get(), get()) }
        factory { CookRecipe(get(), get(), get(), get()) }
    }
