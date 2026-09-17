package com.kitchenai.ui.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleIdToken

/**
 * The OAuth Web Client ID from Firebase's Google sign-in provider (Firebase console ->
 * Authentication -> Sign-in method -> Google -> Web SDK configuration, generated once Google is
 * turned on there). Swapping in the real value is the only step left before this can reach a
 * live Google Cloud project — no other line in this file changes.
 */
private const val GOOGLE_WEB_CLIENT_ID = "TODO-GOOGLE-WEB-CLIENT-ID"

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher {
    val context = LocalContext.current
    return remember(context) { CredentialManagerGoogleSignInLauncher(context) }
}

private class CredentialManagerGoogleSignInLauncher(
    private val context: Context,
) : GoogleSignInLauncher {
    private val credentialManager = CredentialManager.create(context)

    override suspend fun launch(): AppResult<GoogleIdToken> {
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        // All Google accounts on the device, not only ones previously used here:
                        // a first sign-in has no "authorized accounts" to filter to.
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                        .build(),
                )
                .build()

        return try {
            credentialManager.getCredential(context, request).credential.toGoogleIdToken()
        } catch (failure: GetCredentialException) {
            // Cancellation arrives as a GetCredentialCancellationException, a subtype of this:
            // no dedicated AppError case exists for it yet, so it is Unknown with the cause kept.
            AppResult.Failure(AppError.Unknown(failure))
        }
    }

    private fun Credential.toGoogleIdToken(): AppResult<GoogleIdToken> {
        if (this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return AppResult.Failure(AppError.Unknown(IllegalStateException("Unexpected credential type: $type")))
        }
        return try {
            AppResult.Success(GoogleIdToken(GoogleIdTokenCredential.createFrom(data).idToken))
        } catch (parsing: GoogleIdTokenParsingException) {
            AppResult.Failure(AppError.Unknown(parsing))
        }
    }
}
