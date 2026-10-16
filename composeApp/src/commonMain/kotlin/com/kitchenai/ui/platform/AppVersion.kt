package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/** The version the store shows for this build, or null when the platform cannot say. */
@Composable
expect fun rememberAppVersion(): String?
