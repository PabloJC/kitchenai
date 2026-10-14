package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitchenai.shared.core.AppResult

/** Presents Apple's own sign-in sheet. A cancelled or failed attempt is an [AppResult.Failure], never a throw. */
interface AppleSignInLauncher {
    suspend fun launch(): AppResult<AppleSignInResult>
}

/** Null where Sign in with Apple is not offered: App Store guideline 4.8 is iOS-only, so Android shows Google alone. */
@Composable
expect fun rememberAppleSignInLauncher(): AppleSignInLauncher?

/** Apple's own button, so its branding and wording follow Apple's guidelines rather than a copy of them. */
@Composable
expect fun AppleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
)
