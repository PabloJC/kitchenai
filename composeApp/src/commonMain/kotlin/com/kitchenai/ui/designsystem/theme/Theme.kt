package com.kitchenai.ui.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable

/**
 * Shapes rather than radii passed at call sites: a component asks for `medium` and this decides
 * what that is. `extraLarge` is what a bottom sheet lands on.
 */
private val KitchenAiShapes =
    Shapes(
        extraSmall = RoundedCornerShape(Dimens.extraSmall),
        small = RoundedCornerShape(Dimens.cornerSmall),
        medium = RoundedCornerShape(Dimens.corner),
        large = RoundedCornerShape(Dimens.cornerLarge),
        extraLarge = RoundedCornerShape(Dimens.extraLarge),
    )

/** The default follows the system: a hardcoded `false` leaves the app light-only on a dark device. */
@Composable
fun KitchenAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = kitchenAiTypography(),
        shapes = KitchenAiShapes,
        content = content,
    )
}
