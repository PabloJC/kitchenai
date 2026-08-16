package com.kitchenai.ui.di

import com.kitchenai.shared.di.initKoin
import com.kitchenai.ui.presentation.session.SessionViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

// One binding per line: every screen issue appends one of its own.
val presentationModule: Module =
    module {
        viewModel { SessionViewModel(get(), get(), get(), get(), get()) }
        includes(pantryPresentationModule)
        includes(profilePresentationModule)
        includes(shoppingPresentationModule)
        includes(suggestionsPresentationModule)
    }

/**
 * Starts Koin with the `:shared` modules plus presentation.
 *
 * `initKoin` lives in `:shared`, which cannot see `:composeApp`, so both entry points call
 * this instead.
 */
fun initKoinUi(
    functionsRegion: String,
    appDeclaration: KoinAppDeclaration = {},
) = initKoin(functionsRegion) {
    appDeclaration()
    modules(presentationModule)
}
