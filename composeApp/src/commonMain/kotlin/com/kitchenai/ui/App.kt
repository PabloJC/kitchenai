package com.kitchenai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.OfflineBanner
import com.kitchenai.ui.designsystem.theme.KitchenAiTheme
import com.kitchenai.ui.navigation.AppShell
import com.kitchenai.ui.navigation.DetailTopBarState
import com.kitchenai.ui.navigation.KitchenAiNavHost
import com.kitchenai.ui.navigation.Route
import com.kitchenai.ui.navigation.ShellDestination
import com.kitchenai.ui.navigation.ShoppingAddRequests
import com.kitchenai.ui.navigation.open
import com.kitchenai.ui.navigation.switchTo
import com.kitchenai.ui.platform.LaunchLinkEffect
import com.kitchenai.ui.presentation.common.AppearanceViewModel
import com.kitchenai.ui.presentation.common.ConnectivityViewModel
import com.kitchenai.ui.presentation.onboarding.OnboardingExit
import com.kitchenai.ui.presentation.onboarding.OnboardingGate
import com.kitchenai.ui.presentation.reminder.ExpiryReminders
import com.kitchenai.ui.presentation.session.SessionGate
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.network_offline_banner
import com.kitchenai.ui.resources.session_retry
import com.kitchenai.ui.resources.shopping_default_list
import com.kitchenai.ui.resources.tab_ideas
import com.kitchenai.ui.resources.tab_pantry
import com.kitchenai.ui.resources.tab_profile
import com.kitchenai.ui.resources.tab_shopping
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The composition root, and the only place in the shell that holds wording. */
@Composable
fun App(onSessionSettled: () -> Unit = {}) {
    val appearance by koinViewModel<AppearanceViewModel>().state.collectAsStateWithLifecycle()
    KitchenAiTheme(darkTheme = appearance.isDark(isSystemInDarkTheme())) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SessionGate(
                defaultListName = stringResource(Res.string.shopping_default_list),
                retryLabel = stringResource(Res.string.session_retry),
                onSettled = onSessionSettled,
            ) { userId ->
                // Keyed by uid: a ViewModel started for the old user keeps listening to its kitchen after a swap.
                key(userId) {
                    OnboardingGate(userId) { landing, onLanded -> Shell(userId, landing, onLanded) }
                }
            }
        }
    }
}

/** The tabs and their screens, opened where the first run left off when it has just ended. */
@Composable
private fun Shell(
    userId: UserId,
    landing: OnboardingExit?,
    onLanded: () -> Unit,
) {
    val navController = rememberNavController()
    val detailTopBar = remember { DetailTopBarState() }
    val connectivity by koinViewModel<ConnectivityViewModel>().state.collectAsStateWithLifecycle()
    LaunchedEffect(landing) {
        when (landing) {
            OnboardingExit.IDEAS -> navController.switchTo(Route.SuggestionsGraph)
            OnboardingExit.PREFERENCES -> navController.switchTo(Route.ProfileGraph)
            null -> return@LaunchedEffect
        }
        onLanded()
    }
    AppShell(
        navController = navController,
        destinations = tabs,
        detailTopBar = detailTopBar,
    ) { padding ->
        // Consumed so the screens' own imePadding() subtracts the bar it already sits above.
        Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
            OfflineBanner(
                visible = !connectivity.isOnline,
                message = stringResource(Res.string.network_offline_banner),
            )
            KitchenAiNavHost(
                navController = navController,
                userId = userId,
                detailTopBar = detailTopBar,
                modifier = Modifier.weight(1f),
            )
        }
    }
    ExpiryReminders(userId)
    val addRequests = koinInject<ShoppingAddRequests>()
    LaunchLinkEffect { target -> navController.open(target, addRequests) }
}

/** Recipe detail and Kitchen are reached from a tab, so they live in its graph and are not tabs. */
private val tabs =
    listOf(
        ShellDestination(Route.Pantry, Res.string.tab_pantry, Icons.Outlined.Kitchen),
        ShellDestination(Route.ShoppingList, Res.string.tab_shopping, Icons.Outlined.ShoppingCart),
        ShellDestination(
            Route.Suggestions,
            Res.string.tab_ideas,
            Icons.AutoMirrored.Outlined.MenuBook,
            graph = Route.SuggestionsGraph,
        ),
        ShellDestination(Route.Profile, Res.string.tab_profile, Icons.Outlined.Person, graph = Route.ProfileGraph),
    )
