package com.kitchenai.ui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Material3Scale = Typography()

/**
 * Material 3's scale with two named deviations: screen titles carry more weight than the
 * default, and body text is looser because an ingredient list is read at arm's length with
 * wet hands. Everything else is Material's, on purpose.
 */
val KitchenAiTypography: Typography =
    Material3Scale.copy(
        headlineMedium = Material3Scale.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = Material3Scale.bodyLarge.copy(lineHeight = 26.sp),
    )
