package com.kitchenai.ui.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.min
import kotlin.math.roundToInt

private const val ASPECT_RATIO = 16f / 9f

/** In landscape a full-width 16:9 photo is the whole window; past this share of its height it is cropped instead. */
private const val MAX_WINDOW_SHARE = 0.4f

/**
 * The slot a recipe photo or its placeholder fills: the full width at 16:9, but never taller than
 * [MAX_WINDOW_SHARE] of the window, so a phone in landscape still shows the card's text.
 */
@Composable
internal fun Modifier.recipeImageSlot(): Modifier {
    val maxHeight = (LocalWindowInfo.current.containerSize.height * MAX_WINDOW_SHARE).roundToInt()
    return layout { measurable, constraints ->
        val width =
            constraints.constrainWidth(
                if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth,
            )
        val natural = (width / ASPECT_RATIO).roundToInt()
        // A window that reports no size yet (0) must not collapse the photo to nothing.
        val capped = if (maxHeight > 0) min(natural, maxHeight) else natural
        // Kept inside the parent's own bounds too, such as a height-limited detail pane.
        val height = constraints.constrainHeight(capped)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }
}
