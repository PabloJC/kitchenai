package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/** Whether the person asked the system for less motion: animations off on Android, Reduce Motion on iOS. */
@Composable
expect fun rememberReducedMotion(): Boolean
