package com.kitchenai.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.theme.KitchenAiTheme
import com.kitchenai.ui.navigation.AppShell
import com.kitchenai.ui.navigation.KitchenAiNavHost
import com.kitchenai.ui.navigation.Route
import com.kitchenai.ui.navigation.ShellDestination
import com.kitchenai.ui.presentation.session.SessionGate
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.placeholder_body
import com.kitchenai.ui.resources.placeholder_title
import com.kitchenai.ui.resources.tab_ideas
import com.kitchenai.ui.resources.tab_pantry
import com.kitchenai.ui.resources.tab_profile
import com.kitchenai.ui.resources.tab_shopping
import org.jetbrains.compose.resources.stringResource

/** The composition root, and the only place in the shell that holds wording. */
@Composable
fun App() {
    KitchenAiTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SessionGate(defaultListName = "Shopping list", retryLabel = "Try again") { userId ->
                val navController = rememberNavController()
                AppShell(navController = navController, destinations = tabs) { padding ->
                    KitchenAiNavHost(
                        navController = navController,
                        userId = userId,
                        placeholder = { _ -> PlaceholderScreen() },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

/** Stands in for a screen that has not landed yet; it goes away one entry at a time. */
@Composable
private fun PlaceholderScreen() {
    EmptyState(
        title = stringResource(Res.string.placeholder_title),
        body = stringResource(Res.string.placeholder_body),
    )
}

/** Recipe detail is reached from a suggestion, so it is not one of these. */
private val tabs =
    listOf(
        ShellDestination(Route.Pantry, Res.string.tab_pantry, Icons.Outlined.Kitchen),
        ShellDestination(Route.ShoppingList, Res.string.tab_shopping, Icons.Outlined.ShoppingCart),
        ShellDestination(Route.Suggestions, Res.string.tab_ideas, Icons.AutoMirrored.Outlined.MenuBook),
        ShellDestination(Route.Profile, Res.string.tab_profile, Icons.Outlined.Person),
    )
