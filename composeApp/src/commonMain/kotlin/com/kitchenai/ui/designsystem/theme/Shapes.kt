package com.kitchenai.ui.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

/**
 * Fully rounded, whatever the size: tags, chips and grouped controls like the servings stepper.
 * None of these fit [androidx.compose.material3.Shapes]'s five-step scale, which tops out at
 * `extraLarge` rather than a shape that stays a stadium at any width.
 */
val PillShape: Shape = RoundedCornerShape(percent = 50)
