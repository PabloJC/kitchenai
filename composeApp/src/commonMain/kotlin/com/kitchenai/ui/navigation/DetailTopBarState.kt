package com.kitchenai.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What a non-tab screen hands the shared top bar: its own title, and optionally one trailing
 * action. [AppShell] owns the bar and no screen gets one of its own, so this is how a screen
 * still gets to say what belongs in it — recipe detail is the only one today.
 *
 * A screen writes into this on composition and clears it on disposal; [AppShell] only reads.
 */
class DetailTopBarState {
    var title: String? by mutableStateOf(null)
    var action: TopBarAction? by mutableStateOf(null)
}

data class TopBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val tint: Color?,
    val onClick: () -> Unit,
)
