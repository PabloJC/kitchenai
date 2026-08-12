package com.kitchenai.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    EmptyState(title = "Nothing here yet", body = "This screen is on its way")
}

/** Recipe detail is reached from a suggestion, so it is not one of these. */
private val tabs =
    listOf(
        ShellDestination(Route.Pantry, "Pantry"),
        ShellDestination(Route.ShoppingList, "Shopping"),
        ShellDestination(Route.Suggestions, "Ideas"),
        ShellDestination(Route.Profile, "Profile"),
    )
