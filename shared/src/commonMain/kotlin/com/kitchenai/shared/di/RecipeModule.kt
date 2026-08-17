package com.kitchenai.shared.di

import com.kitchenai.shared.domain.usecase.recipe.GetRecipeByIdUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetStoredRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetStoredSuggestionsUseCase
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantryUseCase
import com.kitchenai.shared.domain.usecase.recipe.ObserveSavedRecipesUseCase
import com.kitchenai.shared.domain.usecase.recipe.RemoveSavedRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.SaveRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.StoreSuggestionsUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** The recipe use cases. `PantryMatcher` is stateless and needs no binding. */
val recipeModule: Module =
    module {
        factory { GetRecipeByIdUseCase(get()) }
        factory { GetStoredRecipeUseCase(get()) }
        factory { GetStoredSuggestionsUseCase(get(), get(), get()) }
        factory { MatchRecipeAgainstPantryUseCase(get(), get(), get()) }
        factory { ObserveSavedRecipesUseCase(get()) }
        factory { RemoveSavedRecipeUseCase(get()) }
        factory { SaveRecipeUseCase(get()) }
        factory { StoreSuggestionsUseCase(get()) }
    }
