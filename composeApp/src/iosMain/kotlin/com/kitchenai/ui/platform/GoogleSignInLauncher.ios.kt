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
 * The OAuth Web Client ID from Firebase's Google sign-in provider — see the constant of the
 * same name in `GoogleSignInLauncher.android.kt`. KMPAuth's current iOS implementation actually
 * configures `GIDSignIn` from Info.plist's `GIDClientID` (see `iosApp/Configuration/Config.xcconfig`)
 * rather than from this value, but the public API still asks for it, so it is passed through.
 */
private const val GOOGLE_WEB_CLIENT_ID = "TODO-GOOGLE-WEB-CLIENT-ID"

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher {
    // create() is a process-wide, idempotent singleton: safe to call every time this enters
    // composition, since only the first call is ever honoured.
    val provider = remember { GoogleAuthProvider.create(GoogleAuthCredentials(serverId = GOOGLE_WEB_CLIENT_ID)) }
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
