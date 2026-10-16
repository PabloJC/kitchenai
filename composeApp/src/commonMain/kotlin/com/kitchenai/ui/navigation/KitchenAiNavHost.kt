package com.kitchenai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.kitchen.KitchenScreen
import com.kitchenai.ui.presentation.pantry.PantryScreen
import com.kitchenai.ui.presentation.profile.ProfileScreen
import com.kitchenai.ui.presentation.shopping.ShoppingScreen
import com.kitchenai.ui.presentation.suggestions.cooking.CookingModeScreen
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailScreen
import com.kitchenai.ui.presentation.suggestions.detail.RecipeUnavailableScreen
import com.kitchenai.ui.presentation.suggestions.list.SuggestionsScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * The whole graph. [userId] travels as a parameter rather than in a singleton: a uid kept in
 * mutable global state outlives the session it belongs to, and each screen takes it as an
 * argument.
 */
@Composable
fun KitchenAiNavHost(
    navController: NavHostController,
    userId: UserId,
    detailTopBar: DetailTopBarState,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Pantry,
        modifier = modifier,
    ) {
        composable<Route.Pantry> { PantryScreen(userId) }
        composable<Route.ShoppingList> { ShoppingScreen(userId) }
        navigation<Route.SuggestionsGraph>(startDestination = Route.Suggestions) {
            composable<Route.Suggestions> {
                SuggestionsScreen(userId, onOpen = { id -> navController.navigate(Route.RecipeDetail(id.value)) })
            }
            composable<Route.RecipeDetail> { entry ->
                // The id survives as a String and is validated here: a route argument is text, and
                // a malformed one must not become an identifier the domain trusts.
                val raw = entry.toRoute<Route.RecipeDetail>().recipeId
                when (val id = RecipeId.of(raw)) {
                    is AppResult.Success ->
                        RecipeDetailScreen(
                            userId,
                            id.data,
                            detailTopBar,
                            onStartCooking = { navController.navigate(Route.CookingMode(raw)) },
                        )
                    is AppResult.Failure -> RecipeUnavailableScreen(onBack = navController::navigateUp)
                }
            }
            composable<Route.CookingMode> { entry ->
                // The detail entry below owns the ViewModel, so servings and the cook action are the
                // ones already on screen; remembered per entry, since it is gone once this one pops.
                val detail = remember(entry) { navController.getBackStackEntry<Route.RecipeDetail>() }
                when (val id = RecipeId.of(entry.toRoute<Route.CookingMode>().recipeId)) {
                    is AppResult.Success ->
                        CookingModeScreen(
                            userId,
                            id.data,
                            detailTopBar,
                            onExit = navController::navigateUp,
                            detail = koinViewModel(viewModelStoreOwner = detail),
                        )
                    is AppResult.Failure -> RecipeUnavailableScreen(onBack = navController::navigateUp)
                }
            }
        }
        navigation<Route.ProfileGraph>(startDestination = Route.Profile) {
            composable<Route.Profile> {
                ProfileScreen(userId, onOpenKitchen = { navController.navigate(Route.Kitchen) })
            }
            composable<Route.Kitchen> { KitchenScreen(userId, detailTopBar) }
        }
    }
}
