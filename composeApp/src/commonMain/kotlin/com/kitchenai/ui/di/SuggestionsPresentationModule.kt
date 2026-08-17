package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.suggestions.RecipeDetailReadsDelegate
import com.kitchenai.ui.presentation.suggestions.RecipeDetailViewModel
import com.kitchenai.ui.presentation.suggestions.RecipeDetailWritesDelegate
import com.kitchenai.ui.presentation.suggestions.SuggestionsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val suggestionsPresentationModule: Module =
    module {
        viewModel { SuggestionsViewModel(get(), get(), get(), get()) }
        factory { RecipeDetailReadsDelegate(get(), get(), get(), get(), get(), get()) }
        factory { RecipeDetailWritesDelegate(get(), get(), get(), get()) }
        viewModel { RecipeDetailViewModel(get(), get()) }
    }
