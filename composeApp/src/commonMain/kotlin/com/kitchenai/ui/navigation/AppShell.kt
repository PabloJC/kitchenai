package com.kitchenai.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kitchenai.ui.platform.backIcon
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_title
import com.kitchenai.ui.resources.nav_back
import org.jetbrains.compose.resources.stringResource

/**
 * The frame every tab is drawn inside. Recipe detail and Kitchen are reached from a tab and are
 * not one: their tab stays selected below, and being off the tab's own screen is what tells this
 * bar to draw a back arrow instead of relying on the caller to say so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    navController: NavHostController,
    destinations: List<ShellDestination>,
    detailTopBar: DetailTopBarState,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val selected = destinations.firstOrNull { destination -> entry.isIn(destination.graph) }
    // The tab whose own screen is showing; null on a screen opened from it.
    val tab = selected?.takeIf { destination -> entry.isAt(destination.route) }

    Scaffold(
        modifier = modifier,
        topBar = {
            // A screen drawing its own bar over its content is the one case with no shared bar.
            if (tab != null || !detailTopBar.hidden) {
                TopAppBar(
                    title = {
                        // A tab keeps its own label; a screen with no tab — the kitchen, cooking mode —
                        // gets to say what its title is instead of settling for a generic one.
                        val title = if (tab == null) detailTopBar.title else null
                        Text(
                            title ?: stringResource(tab?.label ?: Res.string.detail_title),
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        // Only a screen opened from a tab needs a way out: every tab is reached by
                        // tapping the bar below, never by going back into it.
                        if (tab == null) {
                            IconButton(onClick = navController::navigateUp) {
                                Icon(
                                    backIcon,
                                    contentDescription = stringResource(Res.string.nav_back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (tab == null) {
                            detailTopBar.action?.let { action -> TopBarActionButton(action) }
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selected,
                        onClick = {
                            if (destination == selected) {
                                navController.popBackStack(destination.route::class, inclusive = false)
                            } else {
                                navController.switchTo(destination.graph)
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
        content = content,
    )
}

/** The hierarchy, not the leaf: a tab stays selected while its own sub-destinations are open. */
private fun NavBackStackEntry?.isIn(graph: Route): Boolean =
    this?.destination?.hierarchy?.any { destination -> destination.hasRoute(graph::class) } == true

private fun NavBackStackEntry?.isAt(route: Route): Boolean = this?.destination?.hasRoute(route::class) == true

/** One entry per tab, each tab keeping the stack it had: tapping a tab twice must not stack it twice. */
internal fun NavHostController.switchTo(route: Route) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TopBarActionButton(action: TopBarAction) {
    val icon: @Composable () -> Unit = {
        Icon(
            action.icon,
            contentDescription = action.contentDescription,
            tint = action.tint ?: LocalContentColor.current,
        )
    }
    val checked = action.checked
    if (checked == null) {
        IconButton(onClick = action.onClick, enabled = action.enabled, content = icon)
    } else {
        IconToggleButton(
            checked = checked,
            onCheckedChange = { action.onClick() },
            enabled = action.enabled,
            content = icon,
        )
    }
}
