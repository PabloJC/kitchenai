package com.kitchenai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kitchenai.shared.domain.model.UserId

/**
 * The whole graph. [userId] travels as a parameter rather than in a singleton: a uid kept in
 * mutable global state outlives the session it belongs to, and each screen takes it as an
 * argument.
 *
 * [placeholder] takes the same argument every real screen will take and stands in until each
 * screen issue replaces its own entry below, which is why every entry is one line: those
 * merges have to stay one-line merges.
 */
@Composable
fun KitchenAiNavHost(
    navController: NavHostController,
    userId: UserId,
    placeholder: @Composable (UserId) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Pantry,
        modifier = modifier,
    ) {
        composable<Route.Pantry> { placeholder(userId) }
        composable<Route.ShoppingList> { placeholder(userId) }
        composable<Route.Suggestions> { placeholder(userId) }
        composable<Route.RecipeDetail> { placeholder(userId) }
        composable<Route.Profile> { placeholder(userId) }
    }
}
