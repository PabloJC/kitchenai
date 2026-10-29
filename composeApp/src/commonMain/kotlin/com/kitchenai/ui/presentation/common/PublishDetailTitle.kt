package com.kitchenai.ui.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.kitchenai.ui.navigation.DetailTopBarState

/** Names a screen opened from a tab in the shared bar while it is showing, and gives the bar back after. */
@Composable
internal fun PublishDetailTitle(
    title: String,
    detailTopBar: DetailTopBarState,
) {
    val owner = remember { Any() }
    DisposableEffect(title) {
        detailTopBar.claim(owner, title)
        onDispose { detailTopBar.release(owner) }
    }
}
