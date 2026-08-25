package com.kitchenai.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_title
import com.kitchenai.ui.resources.nav_back
import org.jetbrains.compose.resources.stringResource

/**
 * The frame every tab is drawn inside. Recipe detail is reached from a tab and is not one, so
 * it never appears in [destinations] — that absence is what tells this bar to draw a back
 * arrow instead of relying on the caller to say so.
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
    val tab = destinations.firstOrNull { entry.isOn(it.route) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    // A tab keeps its own label; a screen with no tab — recipe detail, today —
                    // gets to say what its title is instead of settling for a generic one.
                    val title = if (tab == null) detailTopBar.title else null
                    Text(title ?: stringResource(tab?.label ?: Res.string.detail_title))
                },
                navigationIcon = {
                    // Only the routes absent from destinations need a way out: every tab is
                    // reached by tapping the bar below, never by going back into it.
                    if (tab == null) {
                        IconButton(onClick = navController::navigateUp) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(Res.string.nav_back),
                            )
                        }
                    }
                },
                actions = {
                    if (tab == null) {
                        detailTopBar.action?.let { action ->
                            IconButton(onClick = action.onClick, enabled = action.enabled) {
                                Icon(
                                    action.icon,
                                    contentDescription = action.contentDescription,
                                    tint = action.tint ?: LocalContentColor.current,
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = entry.isOn(destination.route),
                        onClick = { navController.switchTo(destination.route) },
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
