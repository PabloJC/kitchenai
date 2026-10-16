package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

/**
 * Hands plain text to the platform's own share sheet, which is how a household passes a recipe around.
 * [anchor] is the share button's bounds in window pixels; only a popover (iPad) needs it.
 */
@Composable
expect fun rememberShareText(): (text: String, anchor: Rect?) -> Unit
