package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import com.kitchenai.ui.navigation.LaunchTarget

/** Runs [onTarget] when the app was opened, or brought forward, by one of its own links. */
@Composable
expect fun LaunchLinkEffect(onTarget: (LaunchTarget) -> Unit)
