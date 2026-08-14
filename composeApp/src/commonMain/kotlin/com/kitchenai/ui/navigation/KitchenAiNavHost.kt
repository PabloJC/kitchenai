package com.kitchenai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.pantry.PantryScreen
import com.kitchenai.ui.presentation.profile.ProfileScreen
import com.kitchenai.ui.presentation.shopping.ShoppingScreen
import com.kitchenai.ui.presentation.suggestions.RecipeDetailScreen
import com.kitchenai.ui.presentation.suggestions.SuggestionsScreen

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
        composable<Route.Pantry> { PantryScreen(userId) }
        composable<Route.ShoppingList> { ShoppingScreen(userId) }
        composable<Route.Suggestions> {
            SuggestionsScreen(userId, onOpen = { id -> navController.navigate(Route.RecipeDetail(id.value)) })
        }
        composable<Route.RecipeDetail> { entry ->
            // The id survives as a String and is validated here: a route argument is text, and
            // a malformed one must not become an identifier the domain trusts.
            val raw = entry.toRoute<Route.RecipeDetail>().recipeId
            when (val id = RecipeId.of(raw)) {
                is AppResult.Success -> RecipeDetailScreen(userId, id.data)
                is AppResult.Failure -> placeholder(userId)
            }
        }
        composable<Route.Profile> { ProfileScreen(userId) }
    }
}
