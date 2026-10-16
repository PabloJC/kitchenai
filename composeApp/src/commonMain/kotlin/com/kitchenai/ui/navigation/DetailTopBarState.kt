package com.kitchenai.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What a non-tab screen hands the shared top bar: its own title, and optionally one trailing
 * action. A screen that draws its own bar over its content (recipe detail, whose photo runs
 * under it) claims [hidden] instead, and [AppShell] draws none.
 *
 * A screen [claim]s it on composition and [release]s it on disposal; [AppShell] only reads.
 */
class DetailTopBarState {
    var title: String? by mutableStateOf(null)
        private set
    var action: TopBarAction? by mutableStateOf(null)
        private set
    var hidden: Boolean by mutableStateOf(false)
        private set
    private var owner: Any? = null

    fun claim(
        owner: Any,
        title: String?,
        action: TopBarAction? = null,
        hidden: Boolean = false,
    ) {
        this.owner = owner
        this.title = title
        this.action = action
        this.hidden = hidden
    }

    /** A no-op unless [owner] claimed last: a screen leaving mid-transition is disposed after the next one claimed. */
    fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        title = null
        action = null
        hidden = false
    }
}

data class TopBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val tint: Color?,
    val onClick: () -> Unit,
    /** Set for an on/off action, so TalkBack and VoiceOver announce its state and not just its label. */
    val checked: Boolean? = null,
)
