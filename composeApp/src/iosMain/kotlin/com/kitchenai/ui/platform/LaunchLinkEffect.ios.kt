package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import com.kitchenai.ui.navigation.LaunchTarget

// iOS quick actions reach the app through its scene delegate, which a later change wires.
@Composable
actual fun LaunchLinkEffect(onTarget: (LaunchTarget) -> Unit) = Unit
