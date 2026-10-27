package com.kitchenai.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.readableWidth
import com.kitchenai.ui.designsystem.motion.SharedTransitions
import com.kitchenai.ui.designsystem.theme.LocalReducedMotion
import com.kitchenai.ui.presentation.kitchen.KitchenScreen
import com.kitchenai.ui.presentation.pantry.PantryScreen
import com.kitchenai.ui.presentation.profile.ProfileScreen
import com.kitchenai.ui.presentation.shopping.ShoppingScreen

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
    // NavHost's own cross-fade, cut to nothing when the system asks for less motion.
    val reduced = LocalReducedMotion.current
    val viewPantry = { navController.switchTo(Route.Pantry) }
    // Around the host, so a recipe photograph can move from one destination into the next.
    SharedTransitions {
        NavHost(
            navController = navController,
            startDestination = Route.Pantry,
            // Every screen at once: on a tablet a single phone column stretched wide is unreadable.
            modifier = modifier.readableWidth(),
            enterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(FADE_MILLIS)) },
            exitTransition = { if (reduced) ExitTransition.None else fadeOut(tween(FADE_MILLIS)) },
        ) {
            composable<Route.Pantry> { PantryScreen(userId) }
            composable<Route.ShoppingList> { ShoppingScreen(userId, onViewPantry = viewPantry, topBar = detailTopBar) }
            suggestionsGraph(navController, userId, detailTopBar)
            navigation<Route.ProfileGraph>(startDestination = Route.Profile) {
                composable<Route.Profile> {
                    ProfileScreen(userId, onOpenKitchen = { navController.navigate(Route.Kitchen) })
                }
                composable<Route.Kitchen> { KitchenScreen(userId, detailTopBar) }
            }
        }
    }
}

/** NavHost's default cross-fade, spelled out so it can be switched off. */
private const val FADE_MILLIS = 700
