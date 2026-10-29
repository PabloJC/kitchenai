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
import com.kitchenai.ui.designsystem.motion.SharedTransitions
import com.kitchenai.ui.designsystem.theme.LocalReducedMotion
import com.kitchenai.ui.presentation.kitchen.KitchenScreen
import com.kitchenai.ui.presentation.pantry.PantryScreen
import com.kitchenai.ui.presentation.profile.LicencesScreen
import com.kitchenai.ui.presentation.profile.PhotoCreditsScreen
import com.kitchenai.ui.presentation.profile.ProfileNavigation
import com.kitchenai.ui.presentation.profile.ProfileScreen
import com.kitchenai.ui.presentation.shopping.ShoppingScreen
import org.koin.compose.koinInject

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
    val viewList = { navController.switchTo(Route.ShoppingList) }
    val ideasRequests = koinInject<IdeasRequests>()
    val ideasOnExpiring = {
        ideasRequests.requestUseExpiring()
        navController.switchTo(Route.SuggestionsGraph)
    }
    // Around the host, so a recipe photograph can move from one destination into the next.
    SharedTransitions {
        NavHost(
            navController = navController,
            startDestination = Route.Pantry,
            // Each destination caps its own width (Readable): on a tablet a phone column stretched
            // wide is unreadable, and Ideas uses the whole width for its two panes instead.
            modifier = modifier,
            enterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(FADE_MILLIS)) },
            exitTransition = { if (reduced) ExitTransition.None else fadeOut(tween(FADE_MILLIS)) },
        ) {
            composable<Route.Pantry> {
                Readable { PantryScreen(userId, onViewList = viewList, onGetIdeas = ideasOnExpiring) }
            }
            composable<Route.ShoppingList> {
                Readable { ShoppingScreen(userId, onViewPantry = viewPantry, topBar = detailTopBar) }
            }
            suggestionsGraph(navController, userId, detailTopBar)
            navigation<Route.ProfileGraph>(startDestination = Route.Profile) {
                composable<Route.Profile> {
                    val navigation =
                        ProfileNavigation(
                            onOpenKitchen = { navController.navigate(Route.Kitchen) },
                            onOpenPhotoCredits = { navController.navigate(Route.PhotoCredits) },
                            onOpenLicences = { navController.navigate(Route.Licences) },
                        )
                    Readable { ProfileScreen(userId, navigation) }
                }
                composable<Route.Kitchen> { Readable { KitchenScreen(userId, detailTopBar) } }
                composable<Route.PhotoCredits> { Readable { PhotoCreditsScreen(detailTopBar) } }
                composable<Route.Licences> { Readable { LicencesScreen(detailTopBar) } }
            }
        }
    }
}

/** NavHost's default cross-fade, spelled out so it can be switched off. */
private const val FADE_MILLIS = 700
