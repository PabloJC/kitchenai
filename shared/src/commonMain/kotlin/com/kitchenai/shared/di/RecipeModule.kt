package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.recipe.GetRecipeById
import com.kitchenai.shared.domain.usecase.recipe.GetStoredRecipe
import com.kitchenai.shared.domain.usecase.recipe.GetStoredSuggestions
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantry
import com.kitchenai.shared.domain.usecase.recipe.ObserveSavedRecipes
import com.kitchenai.shared.domain.usecase.recipe.RemoveSavedRecipe
import com.kitchenai.shared.domain.usecase.recipe.SaveRecipe
import com.kitchenai.shared.domain.usecase.recipe.StoreSuggestions
import org.koin.core.module.Module
import org.koin.dsl.module

/** The recipe use cases. `PantryMatcher` is stateless and needs no binding. */
val recipeModule: Module =
    module {
        factory { GetRecipeById(get()) }
        factory { GetStoredRecipe(get()) }
        factory { GetStoredSuggestions(get(), get(), get()) }
        factory { MatchRecipeAgainstPantry(get(), get(), get()) }
        factory { ObserveSavedRecipes(get()) }
        factory { RemoveSavedRecipe(get()) }
        factory { SaveRecipe(get()) }
        factory { StoreSuggestions(get()) }
    }
