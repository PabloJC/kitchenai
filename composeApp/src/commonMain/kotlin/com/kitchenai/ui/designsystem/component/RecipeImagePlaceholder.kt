package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens

private const val ASPECT_RATIO = 16f / 9f

/**
 * Where a photograph will go once the product has one. A flat tonal block sized to the slot a
 * real image will occupy, marked with a generic picture glyph — the same one an image loader
 * shows before or instead of a real photo — so adding one later is a data change rather than a
 * layout change.
 *
 * The glyph names "a picture goes here", never a dish: a plate or a specific food icon would
 * claim to know what is being served, and pretending there is a picture would be a worse lie
 * than admitting there is none.
 */
@Composable
fun RecipeImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(Dimens.touchTarget),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
