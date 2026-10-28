package com.kitchenai.ui.designsystem.motion

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.LocalReducedMotion

/**
 * Loading to rows, or rows to empty, fades rather than flashes. Keyed on the phase alone, so a row
 * changing inside the content does not fade the whole list; with reduced motion it cuts.
 */
@Composable
fun PhaseCrossfade(
    phase: ScreenPhase,
    modifier: Modifier = Modifier,
    content: @Composable (ScreenPhase) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    Crossfade(
        targetState = phase,
        modifier = modifier,
        animationSpec = if (reduced) snap() else tween(PHASE_FADE_MILLIS),
        label = "screen-phase",
        content = content,
    )
}

private const val PHASE_FADE_MILLIS = 220
