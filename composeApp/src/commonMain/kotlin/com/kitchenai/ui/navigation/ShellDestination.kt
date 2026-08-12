package com.kitchenai.ui.navigation

/**
 * One tab of the bottom bar. The wording is a parameter because the shell must not hold
 * literals: it is the composition root that owns every string the user reads.
 */
data class ShellDestination(
    val route: Route,
    val label: String,
)
