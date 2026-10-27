package com.kitchenai.ui.navigation

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.motion.AnimatedDestination
import com.kitchenai.ui.presentation.suggestions.cooking.CookingModeScreen
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailRoutes
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailScreen
import com.kitchenai.ui.presentation.suggestions.detail.RecipeUnavailableScreen
import com.kitchenai.ui.presentation.suggestions.list.SuggestionsScreen
import org.koin.compose.viewmodel.koinViewModel

/** Ideas, a recipe and cooking it: one tab's stack. The list and the detail share the photograph's move. */
internal fun NavGraphBuilder.suggestionsGraph(
    navController: NavHostController,
    userId: UserId,
    detailTopBar: DetailTopBarState,
) {
    navigation<Route.SuggestionsGraph>(startDestination = Route.Suggestions) {
        composable<Route.Suggestions> {
            AnimatedDestination(this) {
                SuggestionsScreen(
                    userId,
                    onOpen = { id -> navController.navigate(Route.RecipeDetail(id.value)) },
                    onGoToPantry = { navController.switchTo(Route.Pantry) },
                )
            }
        }
        composable<Route.RecipeDetail> { entry ->
            // The id survives as a String and is validated here: a route argument is text, and
            // a malformed one must not become an identifier the domain trusts.
            val raw = entry.toRoute<Route.RecipeDetail>().recipeId
            when (val id = RecipeId.of(raw)) {
                is AppResult.Success ->
                    AnimatedDestination(this) {
                        RecipeDetailScreen(
                            userId,
                            id.data,
                            detailTopBar,
                            RecipeDetailRoutes(
                                back = navController::navigateUp,
                                startCooking = { navController.navigate(Route.CookingMode(raw)) },
                                shoppingList = { navController.switchTo(Route.ShoppingList) },
                            ),
                        )
                    }
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
}
