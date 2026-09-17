package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.mmk.kmpauth.google.GoogleAuthCredentials
import com.mmk.kmpauth.google.GoogleAuthProvider
import com.mmk.kmpauth.google.GoogleAuthUiProvider

/**
 * Deliberately not the real Web Client ID, and not bound through Koin the way the Android
 * launcher's is: KMPAuth's current iOS implementation configures `GIDSignIn` from Info.plist's
 * `GIDClientID` (`iosApp/Configuration/Config.xcconfig`) instead of reading this value — the
 * public API still requires a `serverId` argument, so this is inert filler, never read for
 * anything real. A different literal from Android's on purpose, so the two are never mistaken
 * for the same configuration (#201 review).
 */
private const val UNUSED_IOS_SERVER_ID_PLACEHOLDER = "unused-on-ios-see-gidclientid-in-info-plist"

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher {
    // create() is a process-wide, idempotent singleton: safe to call every time this enters
    // composition, since only the first call is ever honoured.
    val provider =
        remember { GoogleAuthProvider.create(GoogleAuthCredentials(serverId = UNUSED_IOS_SERVER_ID_PLACEHOLDER)) }
    val uiProvider = provider.getUiProvider()
    return remember(uiProvider) { KmpAuthGoogleSignInLauncher(uiProvider) }
}

private class KmpAuthGoogleSignInLauncher(
    private val uiProvider: GoogleAuthUiProvider,
) : GoogleSignInLauncher {
    override suspend fun launch(): AppResult<GoogleIdToken> =
        uiProvider.signIn().fold(
            onSuccess = { user -> AppResult.Success(GoogleIdToken(user.idToken)) },
            // Covers both a platform error and the user dismissing the chooser: KMPAuth
            // reports cancellation as a failed Result too, never a thrown exception.
            onFailure = { error -> AppResult.Failure(AppError.Unknown(error)) },
        )
}
