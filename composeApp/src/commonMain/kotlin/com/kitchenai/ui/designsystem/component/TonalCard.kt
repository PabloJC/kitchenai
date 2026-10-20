package com.kitchenai.ui.designsystem.component

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The one card surface: a tonal step above the page rather than a shadow, the honey-and-sage
 * palette's signature. Paired with the large corner, which also clips a card's image.
 */
@Composable
fun tonalCardColors(): CardColors =
    CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
