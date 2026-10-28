package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.suggestions.cooking.CookingModeViewModel
import com.kitchenai.ui.presentation.suggestions.cooking.RunningTimers
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailReadsDelegate
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailViewModel
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailWritesDelegate
import com.kitchenai.ui.presentation.suggestions.list.SuggestionsReadsDelegate
import com.kitchenai.ui.presentation.suggestions.list.SuggestionsViewModel
import com.kitchenai.ui.presentation.suggestions.list.SuggestionsWritesDelegate
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val suggestionsPresentationModule: Module =
    module {
        factory { SuggestionsReadsDelegate(get(), get(), get(), get()) }
        factory { SuggestionsWritesDelegate(get(), get(), get(), get()) }
        viewModel { SuggestionsViewModel(get(), get(), get(), get(), get(), get(), get()) }
        factory { RecipeDetailReadsDelegate(get(), get(), get(), get(), get(), get(), get(), get()) }
        factory { RecipeDetailWritesDelegate(get(), get(), get(), get(), get(), get(), get()) }
        viewModel { RecipeDetailViewModel(get(), get(), get()) }
        single { RunningTimers() }
        viewModel { CookingModeViewModel(get(), get(), get()) }
    }
