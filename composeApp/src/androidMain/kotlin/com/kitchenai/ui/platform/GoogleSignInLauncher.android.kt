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
import com.kitchenai.ui.di.GOOGLE_WEB_CLIENT_ID
import org.koin.compose.koinInject

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher {
    val context = LocalContext.current
    // Bound from BuildConfig.GOOGLE_WEB_CLIENT_ID (gradle.properties -> androidApp -> Koin),
    // not a constant here: an OAuth client id is environment configuration, the same reasoning
    // FUNCTIONS_REGION already follows (#201 review).
    val webClientId = koinInject<String>(qualifier = GOOGLE_WEB_CLIENT_ID)
    return remember(context, webClientId) { CredentialManagerGoogleSignInLauncher(context, webClientId) }
}

private class CredentialManagerGoogleSignInLauncher(
    private val context: Context,
    private val webClientId: String,
) : GoogleSignInLauncher {
    private val credentialManager = CredentialManager.create(context)

    override suspend fun launch(): AppResult<GoogleSignInResult> {
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        // All Google accounts on the device, not only ones previously used here:
                        // a first sign-in has no "authorized accounts" to filter to.
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .setServerClientId(webClientId)
                        .build(),
                )
                .build()

        return try {
            credentialManager.getCredential(context, request).credential.toGoogleSignInResult()
        } catch (failure: GetCredentialException) {
            // Cancellation arrives as a GetCredentialCancellationException, a subtype of this:
            // no dedicated AppError case exists for it yet, so it is Unknown with the cause kept.
            AppResult.Failure(AppError.Unknown(failure))
        }
    }

    private fun Credential.toGoogleSignInResult(): AppResult<GoogleSignInResult> {
        if (this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return AppResult.Failure(AppError.Unknown(IllegalStateException("Unexpected credential type: $type")))
        }
        return try {
            val credential = GoogleIdTokenCredential.createFrom(data)
            AppResult.Success(GoogleSignInResult(GoogleIdToken(credential.idToken), credential.displayName))
        } catch (parsing: GoogleIdTokenParsingException) {
            AppResult.Failure(AppError.Unknown(parsing))
        }
    }
}
