package com.kitchenai.ui.di

import com.kitchenai.ui.platform.CookingTimerScheduler
import com.kitchenai.ui.platform.ExpiryReminderScheduler
import com.kitchenai.ui.platform.NetworkMonitor
import com.kitchenai.ui.platform.platformCookingTimerScheduler
import com.kitchenai.ui.platform.platformExpiryReminderScheduler
import com.kitchenai.ui.platform.platformNetworkMonitor
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier for the bound Google Web Client ID; keeps `koinInject<String>` unambiguous. */
val GOOGLE_WEB_CLIENT_ID = named("googleWebClientId")

/** Qualifier for the privacy policy's address, which build configuration supplies; blank until one is published. */
val PRIVACY_POLICY_URL = named("privacyPolicyUrl")

/**
 * [googleWebClientId] is a parameter rather than a constant for the same reason
 * `agentDataModule`'s `functionsRegion` is: an environment-specific value that only Android's
 * `BuildConfig` (from `gradle.properties`) knows, not something a `:composeApp` file can hardcode
 * (`GoogleSignInLauncher.android.kt`, #188/#201 review). Blank on iOS, which sources its own
 * client id from Info.plist instead.
 */
fun platformModule(
    googleWebClientId: String,
    privacyPolicyUrl: String = "",
): Module =
    module {
        single(GOOGLE_WEB_CLIENT_ID) { googleWebClientId }
        single(PRIVACY_POLICY_URL) { privacyPolicyUrl }
        single<NetworkMonitor> { platformNetworkMonitor() }
        single<ExpiryReminderScheduler> { platformExpiryReminderScheduler() }
        single<CookingTimerScheduler> { platformCookingTimerScheduler() }
    }
