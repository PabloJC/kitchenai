package com.kitchenai.ui.di

import com.kitchenai.shared.di.FirebaseEmulators
import com.kitchenai.shared.di.initKoin
import com.kitchenai.ui.navigation.IdeasRequests
import com.kitchenai.ui.navigation.LaunchLinks
import com.kitchenai.ui.navigation.ShoppingAddRequests
import com.kitchenai.ui.presentation.common.AppearanceViewModel
import com.kitchenai.ui.presentation.common.ConnectivityViewModel
import com.kitchenai.ui.presentation.common.DisplayNameViewModel
import com.kitchenai.ui.presentation.common.PendingDisplayName
import com.kitchenai.ui.presentation.session.SessionKitchenDelegate
import com.kitchenai.ui.presentation.session.SessionViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

// One binding per line: every screen issue appends one of its own.
val presentationModule: Module =
    module {
        single { PendingDisplayName() }
        single { ShoppingAddRequests() }
        single { IdeasRequests() }
        single { LaunchLinks() }
        viewModel { ConnectivityViewModel(get()) }
        viewModel { AppearanceViewModel(get(), get()) }
        viewModel { DisplayNameViewModel(get(), get()) }
        factory { SessionKitchenDelegate(get(), get(), get(), get()) }
        viewModel { SessionViewModel(get(), get(), get(), get(), get(), get(), get()) }
        includes(kitchenPresentationModule)
        includes(onboardingPresentationModule)
        includes(pantryPresentationModule)
        includes(profilePresentationModule)
        includes(reminderPresentationModule)
        includes(shoppingPresentationModule)
        includes(suggestionsPresentationModule)
    }

/**
 * Starts Koin with the `:shared` modules plus presentation.
 *
 * `initKoin` lives in `:shared`, which cannot see `:composeApp`, so both entry points call
 * this instead. A blank [firebaseEmulatorHost] is production; only debug builds pass one.
 */
fun initKoinUi(
    functionsRegion: String,
    googleWebClientId: String = "",
    privacyPolicyUrl: String = "",
    firebaseEmulatorHost: String = "",
    firestoreEmulatorPort: Int = FirebaseEmulators.DEFAULT_FIRESTORE_PORT,
    appDeclaration: KoinAppDeclaration = {},
) = initKoin(functionsRegion, FirebaseEmulators.from(firebaseEmulatorHost, firestoreEmulatorPort)) {
    appDeclaration()
    modules(presentationModule, platformModule(googleWebClientId, privacyPolicyUrl))
}
