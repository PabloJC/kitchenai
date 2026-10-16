package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/** Hands plain text to the platform's own share sheet, which is how a household passes a recipe around. */
@Composable
expect fun rememberShareText(): (String) -> Unit
