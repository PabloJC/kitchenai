package com.kitchenai.ui.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kitchenai.shared.core.AppResult
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.AppleSignInButton
import com.kitchenai.ui.platform.AppleSignInLauncher
import com.kitchenai.ui.platform.rememberAppleSignInLauncher
import com.kitchenai.ui.platform.rememberGoogleSignInLauncher
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.onboarding_sign_in_google
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * "I already have an account", for a returning user on a new phone: Apple first on iOS, as App
 * Store guideline 4.8 asks, then Google. Each sheet opens once at a time, as Profile's do.
 */
@Composable
internal fun OnboardingSignIn(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    val google = rememberGoogleSignInLauncher()
    val apple = rememberAppleSignInLauncher()
    val scope = rememberCoroutineScope()
    var launching by remember { mutableStateOf(false) }
    val enabled = !launching && !state.isBusy

    fun runSheet(sheet: suspend () -> Unit) {
        if (launching) return
        launching = true
        scope.launch {
            try {
                sheet()
            } finally {
                launching = false
            }
        }
    }

    // Apple spent its nonce on the refused link: one more sheet carries the switch.
    LaunchedEffect(state.appleSwitchPending) {
        if (state.appleSwitchPending && apple != null) runSheet { appleSheet(apple, viewModel, switch = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
        apple?.let { launcher ->
            AppleSignInButton(
                onClick = { runSheet { appleSheet(launcher, viewModel, switch = false) } },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedButton(
            onClick = {
                runSheet {
                    when (val result = google.launch()) {
                        is AppResult.Success ->
                            viewModel.signInWithGoogle(
                                result.data.token,
                                result.data.displayName,
                                result.data.accessToken,
                            )
                        is AppResult.Failure -> viewModel.onSignInSheetFailed(result.error)
                    }
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.onboarding_sign_in_google))
        }
    }
}

private suspend fun appleSheet(
    launcher: AppleSignInLauncher,
    viewModel: OnboardingViewModel,
    switch: Boolean,
) {
    when (val result = launcher.launch()) {
        is AppResult.Success ->
            if (switch) {
                viewModel.switchToAppleAccount(result.data.token, result.data.rawNonce, result.data.displayName)
            } else {
                viewModel.signInWithApple(result.data.token, result.data.rawNonce, result.data.displayName)
            }
        is AppResult.Failure -> viewModel.onSignInSheetFailed(result.error)
    }
}
