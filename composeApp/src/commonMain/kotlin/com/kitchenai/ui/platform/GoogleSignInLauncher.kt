package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import com.kitchenai.shared.core.AppResult

/**
 * Presents the platform's own Google Sign-In UI. A cancelled or failed attempt is an
 * [AppResult.Failure] — never a thrown exception, since #189's caller cannot know every SDK's
 * exception type.
 */
interface GoogleSignInLauncher {
    suspend fun launch(): AppResult<GoogleSignInResult>
}

/**
 * Binds a [GoogleSignInLauncher] to this screen's platform context: an `Activity` on Android
 * (via `LocalContext.current`), the key window's root view controller on iOS. A plain top-level
 * `expect fun` cannot reach either, which is why this is `@Composable` instead.
 */
@Composable
expect fun rememberGoogleSignInLauncher(): GoogleSignInLauncher
