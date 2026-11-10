package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kitchenai.ui.designsystem.theme.Dimens

/** A catalogue ingredient's picture as a row's leading image; the tonal square stays if it never loads. */
@Composable
fun IngredientThumbnail(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model =
            ImageRequest
                .Builder(LocalPlatformContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
        // Decorative: the row's headline already names the ingredient.
        contentDescription = null,
        modifier =
            modifier
                .size(Dimens.thumbnail)
                .clip(RoundedCornerShape(Dimens.cornerSmall))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
    )
}
