package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val ASPECT_RATIO = 16f / 9f

/**
 * Where a photograph will go once the product has one. A flat tonal block sized to the slot a
 * real image will occupy, so adding one later is a data change rather than a layout change.
 *
 * A plain surface colour rather than a stock image or a plate icon: pretending there is a
 * picture would be a worse lie than admitting there is none.
 */
@Composable
fun RecipeImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
