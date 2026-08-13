package com.kitchenai.ui.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Honey & Sage: a honey primary and a sage secondary over warm neutrals. The light scheme is the
 * design export, literal. The dark one is derived from the same tonal palettes, which the export
 * did not carry — the `*Fixed` roles pin tones 10, 30, 80 and 90 of each palette, and the dark
 * roles are the same tones in the order Material 3 assigns them.
 *
 * This file is the only place in the app allowed to name a colour.
 */
private object Honey {
    val t20 = Color(0xFF4C2700)
    val t30 = Color(0xFF6E3900)
    val t40 = Color(0xFF8D4B00)
    val t80 = Color(0xFFFFB77D)
    val t90 = Color(0xFFFFDCC3)
}

private object Sage {
    val t20 = Color(0xFF2A3400)
    val t30 = Color(0xFF404B1B)
    val t40 = Color(0xFF586330)
    val t80 = Color(0xFFBFCD8F)
    val t90 = Color(0xFFDBE9A9)
}

private object Leaf {
    val t20 = Color(0xFF273417)
    val t30 = Color(0xFF3D4B2B)
    val t40 = Color(0xFF51613F)
    val t80 = Color(0xFFBBCDA3)
    val t90 = Color(0xFFD7E9BD)
}

/** Warm greys, and the browns the outlines are drawn in. */
private object Neutral {
    val surfaceDark = Color(0xFF121414)
    val containerLowestDark = Color(0xFF0D0E0E)
    val containerLowDark = Color(0xFF1A1C1C)
    val containerDark = Color(0xFF1E2020)
    val containerHighDark = Color(0xFF292A2A)
    val containerHighestDark = Color(0xFF343635)
    val onSurfaceDark = Color(0xFFE3E2E1)
    val outlineDark = Color(0xFFA38D7C)
    val outlineVariantDark = Color(0xFF554336)
    val onSurfaceVariantDark = Color(0xFFDBC2B0)
}

internal val LightColors =
    lightColorScheme(
        primary = Honey.t40,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB15F00),
        onPrimaryContainer = Color(0xFFFFFBFF),
        inversePrimary = Honey.t80,
        secondary = Sage.t40,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD8E6A6),
        onSecondaryContainer = Color(0xFF5C6834),
        tertiary = Leaf.t40,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF6A7A56),
        onTertiaryContainer = Color(0xFFF9FFEB),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFFAF9F8),
        onBackground = Color(0xFF1A1C1C),
        surface = Color(0xFFFAF9F8),
        onSurface = Color(0xFF1A1C1C),
        surfaceVariant = Color(0xFFE3E2E1),
        onSurfaceVariant = Color(0xFF554336),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F3F2),
        surfaceContainer = Color(0xFFEEEEED),
        surfaceContainerHigh = Color(0xFFE9E8E7),
        surfaceContainerHighest = Color(0xFFE3E2E1),
        surfaceDim = Color(0xFFDADAD9),
        surfaceBright = Color(0xFFFAF9F8),
        outline = Color(0xFF887364),
        outlineVariant = Color(0xFFDBC2B0),
        inverseSurface = Color(0xFF2F3130),
        inverseOnSurface = Color(0xFFF1F0F0),
        surfaceTint = Color(0xFF904D00),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Honey.t80,
        onPrimary = Honey.t20,
        primaryContainer = Honey.t30,
        onPrimaryContainer = Honey.t90,
        inversePrimary = Honey.t40,
        secondary = Sage.t80,
        onSecondary = Sage.t20,
        secondaryContainer = Sage.t30,
        onSecondaryContainer = Sage.t90,
        tertiary = Leaf.t80,
        onTertiary = Leaf.t20,
        tertiaryContainer = Leaf.t30,
        onTertiaryContainer = Leaf.t90,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Neutral.surfaceDark,
        onBackground = Neutral.onSurfaceDark,
        surface = Neutral.surfaceDark,
        onSurface = Neutral.onSurfaceDark,
        surfaceVariant = Neutral.outlineVariantDark,
        onSurfaceVariant = Neutral.onSurfaceVariantDark,
        surfaceContainerLowest = Neutral.containerLowestDark,
        surfaceContainerLow = Neutral.containerLowDark,
        surfaceContainer = Neutral.containerDark,
        surfaceContainerHigh = Neutral.containerHighDark,
        surfaceContainerHighest = Neutral.containerHighestDark,
        surfaceDim = Neutral.surfaceDark,
        surfaceBright = Neutral.containerHighestDark,
        outline = Neutral.outlineDark,
        outlineVariant = Neutral.outlineVariantDark,
        inverseSurface = Neutral.onSurfaceDark,
        inverseOnSurface = Color(0xFF2F3130),
        surfaceTint = Honey.t80,
    )
