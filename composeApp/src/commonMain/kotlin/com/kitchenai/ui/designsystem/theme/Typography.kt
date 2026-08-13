package com.kitchenai.ui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.inter_bold
import com.kitchenai.ui.resources.inter_medium
import com.kitchenai.ui.resources.inter_regular
import com.kitchenai.ui.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/**
 * Bundled rather than taken from the system: the two platforms default to different faces, and a
 * recipe that reflows between them is a different screen. Static weights, not the variable file,
 * which Compose resolves unevenly on iOS.
 */
@Composable
private fun interFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )

/**
 * The design's seven roles mapped onto the Material 3 scale rather than named again: components
 * ask for `titleMedium`, and only this file knows what that is in Inter.
 *
 * Body sits at 16sp because the phone is at arm's length on a counter, and step text carries a
 * 26sp line height for the same reason.
 */
@Composable
internal fun kitchenAiTypography(): Typography {
    val inter = interFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = inter, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = inter, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = inter, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = inter, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = inter),
        bodySmall = base.bodySmall.copy(fontFamily = inter),
        labelLarge = base.labelLarge.copy(fontFamily = inter, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = inter, fontWeight = FontWeight.Medium),
        // Metadata: 11sp, uppercased at the call site, spaced so it reads as a label and not as prose.
        labelSmall = base.labelSmall.copy(fontFamily = inter, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    )
}
