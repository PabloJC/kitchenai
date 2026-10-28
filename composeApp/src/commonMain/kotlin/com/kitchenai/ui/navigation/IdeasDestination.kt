package com.kitchenai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailRoutes
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailScreen
import com.kitchenai.ui.presentation.suggestions.list.SuggestionsScreen
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.suggestions_pane_empty_body
import com.kitchenai.ui.resources.suggestions_pane_empty_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Ideas on a narrow window is a list that opens a recipe on its own screen. On a wide one the
 * recipe opens beside the list instead, so a tablet in landscape shows both at once.
 */
@Composable
internal fun IdeasDestination(
    navController: NavHostController,
    userId: UserId,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < TWO_PANE_MIN_WIDTH) {
            Readable {
                SuggestionsScreen(
                    userId,
                    onOpen = { id -> navController.navigate(Route.RecipeDetail(id.value)) },
                    onGoToPantry = { navController.switchTo(Route.Pantry) },
                )
            }
        } else {
            TwoPanes(navController, userId)
        }
    }
}

@Composable
private fun TwoPanes(
    navController: NavHostController,
    userId: UserId,
) {
    // A String, so it survives rotation and process death; validated where it becomes an id.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(LIST_WEIGHT).fillMaxHeight()) {
            SuggestionsScreen(
                userId,
                onOpen = { id -> selected = id.value },
                onGoToPantry = { navController.switchTo(Route.Pantry) },
            )
        }
        VerticalDivider()
        Box(Modifier.weight(DETAIL_WEIGHT).fillMaxHeight()) {
            val id = selected?.let { raw -> (RecipeId.of(raw) as? AppResult.Success)?.data }
            if (id == null) {
                EmptyState(
                    title = stringResource(Res.string.suggestions_pane_empty_title),
                    body = stringResource(Res.string.suggestions_pane_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                key(id.value) {
                    RecipeDetailScreen(
                        userId,
                        id,
                        detailTopBar = null,
                        routes =
                            RecipeDetailRoutes(
                                back = { selected = null },
                                // Through the detail destination: cooking mode reads its servings from there.
                                startCooking = {
                                    navController.navigate(Route.RecipeDetail(id.value))
                                    navController.navigate(Route.CookingMode(id.value))
                                },
                                shoppingList = { navController.switchTo(Route.ShoppingList) },
                            ),
                        viewModel = koinViewModel(key = "pane-${id.value}"),
                    )
                }
            }
        }
    }
}

/** Where two panes start to fit: Material's expanded width class. */
private val TWO_PANE_MIN_WIDTH = 840.dp
private const val LIST_WEIGHT = 1f
private const val DETAIL_WEIGHT = 1.4f
