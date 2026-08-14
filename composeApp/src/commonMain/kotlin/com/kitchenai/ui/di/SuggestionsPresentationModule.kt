package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.suggestions.RecipeDetailReads
import com.kitchenai.ui.presentation.suggestions.RecipeDetailViewModel
import com.kitchenai.ui.presentation.suggestions.RecipeDetailWrites
import com.kitchenai.ui.presentation.suggestions.SuggestionCache
import com.kitchenai.ui.presentation.suggestions.SuggestionsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val suggestionsPresentationModule: Module =
    module {
        // A single: the two screens have to see the same generation.
        single { SuggestionCache() }
        viewModel { SuggestionsViewModel(get(), get(), get(), get()) }
        factory { RecipeDetailReads(get(), get(), get(), get(), get(), get()) }
        factory { RecipeDetailWrites(get(), get(), get(), get()) }
        viewModel { RecipeDetailViewModel(get(), get(), get()) }
    }
