package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun rememberAppleSignInLauncher(): AppleSignInLauncher? = null

/** Never composed: with no launcher there is no Apple button on Android. */
@Composable
actual fun AppleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) = Unit
