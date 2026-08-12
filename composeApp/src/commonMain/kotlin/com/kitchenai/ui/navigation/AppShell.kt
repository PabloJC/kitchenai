package com.kitchenai.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * The frame every tab is drawn inside. Recipe detail is reached from a tab and is not one, so
 * it never appears in [destinations].
 */
@Composable
fun AppShell(
    navController: NavHostController,
    destinations: List<ShellDestination>,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = entry.isOn(destination.route),
                        onClick = { navController.switchTo(destination.route) },
                        // No icon set is on the classpath for both targets yet, and inventing one
                        // per screen is a design decision this issue does not get to make.
                        icon = { },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
        content = content,
    )
}

/** The hierarchy, not the leaf: a tab stays selected while its own sub-destinations are open. */
private fun NavBackStackEntry?.isOn(route: Route): Boolean =
    this?.destination?.hierarchy?.any { destination -> destination.hasRoute(route::class) } == true

/** One entry per tab, each tab keeping the stack it had: tapping a tab twice must not stack it twice. */
private fun NavHostController.switchTo(route: Route) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
