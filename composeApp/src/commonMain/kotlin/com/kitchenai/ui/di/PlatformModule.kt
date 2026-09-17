package com.kitchenai.ui.di

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier for the bound Google Web Client ID; keeps `koinInject<String>` unambiguous. */
val GOOGLE_WEB_CLIENT_ID = named("googleWebClientId")

/**
 * [googleWebClientId] is a parameter rather than a constant for the same reason
 * `agentDataModule`'s `functionsRegion` is: an environment-specific value that only Android's
 * `BuildConfig` (from `gradle.properties`) knows, not something a `:composeApp` file can hardcode
 * (`GoogleSignInLauncher.android.kt`, #188/#201 review). Blank on iOS, which sources its own
 * client id from Info.plist instead.
 */
fun platformModule(googleWebClientId: String): Module =
    module {
        single(GOOGLE_WEB_CLIENT_ID) { googleWebClientId }
    }
