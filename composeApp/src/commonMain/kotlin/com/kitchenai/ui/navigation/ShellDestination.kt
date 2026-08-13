package com.kitchenai.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One tab of the bottom bar. The wording and the icon are parameters because the shell must not
 * hold literals: it is the composition root that owns every string the user reads.
 */
data class ShellDestination(
    val route: Route,
    val label: String,
    val icon: ImageVector,
)
