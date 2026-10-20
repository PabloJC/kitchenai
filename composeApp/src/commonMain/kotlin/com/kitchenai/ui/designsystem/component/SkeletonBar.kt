package com.kitchenai.ui.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.designsystem.theme.LocalReducedMotion

private const val MIN_ALPHA = 0.4f
private const val MAX_ALPHA = 1f
private const val PULSE_MILLIS = 800
private const val STILL_ALPHA = 0.7f

/** Width fractions for a [SkeletonBar] standing in for a title or a summary line. */
const val SKELETON_TITLE_WIDTH = 0.7f
const val SKELETON_SUMMARY_WIDTH = 0.85f

/**
 * A pulsing bar standing in for a line of text that has not loaded yet. The pulse is what tells
 * it apart from [RecipeImagePlaceholder]: that block is honest about staying empty, this one is
 * honest about being temporary.
 */
@Composable
fun SkeletonBar(modifier: Modifier = Modifier) {
    // Held still when the system asks for less motion; the shape alone still reads as "loading".
    val alpha = if (LocalReducedMotion.current) STILL_ALPHA else pulsingAlpha()
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimens.cornerSmall))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
    )
}

@Composable
private fun pulsingAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by
        transition.animateFloat(
            initialValue = MIN_ALPHA,
            targetValue = MAX_ALPHA,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    return alpha
}
