package com.kitchenai.ui.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** True when the system asks for less motion: loops stop and transitions cut instead of moving. */
val LocalReducedMotion = staticCompositionLocalOf { false }
