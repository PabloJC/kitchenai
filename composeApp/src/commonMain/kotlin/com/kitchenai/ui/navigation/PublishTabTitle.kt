package com.kitchenai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/** Names [route]'s tab in the shared bar while this call is composed, and hands the label back on exit. */
@Composable
fun PublishTabTitle(
    state: DetailTopBarState,
    route: Route,
    title: String,
) {
    val owner = remember { Any() }
    DisposableEffect(title) {
        state.claimTabTitle(owner, route, title)
        onDispose { state.releaseTabTitle(owner) }
    }
}
