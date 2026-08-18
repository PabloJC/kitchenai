package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailReadsDelegate
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailViewModel
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailWritesDelegate
import com.kitchenai.ui.presentation.suggestions.list.SuggestionsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val suggestionsPresentationModule: Module =
    module {
        viewModel { SuggestionsViewModel(get(), get(), get(), get(), get(), get()) }
        factory { RecipeDetailReadsDelegate(get(), get(), get(), get(), get(), get()) }
        factory { RecipeDetailWritesDelegate(get(), get(), get(), get()) }
        viewModel { RecipeDetailViewModel(get(), get()) }
    }
