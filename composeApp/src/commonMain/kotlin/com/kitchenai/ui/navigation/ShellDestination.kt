package com.kitchenai.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

/**
 * One tab of the bottom bar. The wording and the icon are parameters because the shell must not
 * hold literals: it is the composition root that owns every string the user reads.
 *
 * [route] is the tab's own screen and [graph] the nested graph holding it and the screens opened
 * from it; a tab with no sub-screens is its own graph.
 */
data class ShellDestination(
    val route: Route,
    /** The key, not the word: the bar is drawn where the reader's language is known. */
    val label: StringResource,
    val icon: ImageVector,
    val graph: Route = route,
)
