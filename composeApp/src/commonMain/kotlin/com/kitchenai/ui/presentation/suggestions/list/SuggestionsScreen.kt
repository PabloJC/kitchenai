package com.kitchenai.ui.presentation.suggestions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.MealType
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.CoverageBar
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.RecipeImagePlaceholder
import com.kitchenai.ui.designsystem.component.SKELETON_SUMMARY_WIDTH
import com.kitchenai.ui.designsystem.component.SKELETON_TITLE_WIDTH
import com.kitchenai.ui.designsystem.component.SkeletonBar
import com.kitchenai.ui.designsystem.component.tonalCardColors
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.navigation.IdeasRequests
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.text
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_undo
import com.kitchenai.ui.resources.shopping_default_list
import com.kitchenai.ui.resources.snack_added_count
import com.kitchenai.ui.resources.snack_counts_joined
import com.kitchenai.ui.resources.snack_not_needed_count
import com.kitchenai.ui.resources.snack_saved
import com.kitchenai.ui.resources.snack_unsaved
import com.kitchenai.ui.resources.suggestions_add_to_pantry
import com.kitchenai.ui.resources.suggestions_empty_body_action
import com.kitchenai.ui.resources.suggestions_empty_title
import com.kitchenai.ui.resources.suggestions_meal_breakfast
import com.kitchenai.ui.resources.suggestions_meal_dinner
import com.kitchenai.ui.resources.suggestions_meal_lunch
import com.kitchenai.ui.resources.suggestions_meal_snack
import com.kitchenai.ui.resources.suggestions_new_ideas
import com.kitchenai.ui.resources.suggestions_nothing_body_action
import com.kitchenai.ui.resources.suggestions_nothing_title
import com.kitchenai.ui.resources.suggestions_only_pantry
import com.kitchenai.ui.resources.suggestions_pantry_empty_body
import com.kitchenai.ui.resources.suggestions_pantry_empty_title
import com.kitchenai.ui.resources.suggestions_quick
import com.kitchenai.ui.resources.suggestions_use_expiring
import com.kitchenai.ui.resources.suggestions_view_ideas
import com.kitchenai.ui.resources.suggestions_view_saved
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val QUICK_MINUTES = 30
private const val SKELETON_CARDS = 3

// Not recipe ids, so they can never collide with a card's key.
private const val EARLIER_ROW_KEY = "earlier-ideas"
private const val OPTIONS_KEY = "options"
private const val OFFLINE_KEY = "offline"
private const val FILTERS_CHANGED_KEY = "filters-changed"
private const val GENERATING_KEY = "generating"
private const val ERROR_KEY = "error"

// Room under the list for the floating button, so it never hides the last card's content.
private val FAB_CLEARANCE = 88.dp

@Composable
fun SuggestionsScreen(
    userId: UserId,
    modifier: Modifier = Modifier,
    onOpen: (RecipeId) -> Unit = {},
    onGoToPantry: () -> Unit = {},
    viewModel: SuggestionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val defaultListName = stringResource(Res.string.shopping_default_list)

    LaunchedEffect(userId) { viewModel.start(userId, platformLanguageTags(), defaultListName) }
    UseExpiringRequests(viewModel)
    LaunchedEffect(viewModel) {
        // Launched apart so the collector keeps draining while an Undo waits; the host still shows them in order.
        viewModel.events.collect { event -> launch { snackbar.announce(event, viewModel::undoUnsave) } }
    }

    // The insets are AppShell's; a second round here would open a gap between the toolbar and
    // the bottom bar rather than closing one.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Generating is the ideas view's action; on the saved view it would add to a list not shown.
            if (!state.showingSaved) {
                NewIdeasButton(enabled = !state.isGenerating && state.isOnline, onClick = viewModel::generate)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = Dimens.large).padding(top = Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            ViewSwitch(
                showingSaved = state.showingSaved,
                savedCount = state.savedRecipes.size,
                onShowSaved = viewModel::showSaved,
            )
            if (state.showingSaved) {
                SavedRecipesView(
                    state,
                    onOpen,
                    onUnsave = viewModel::unsave,
                    onSearch = viewModel::searchSaved,
                    onSeeIdeas = { viewModel.showSaved(false) },
                )
            } else {
                // The filters and banners scroll with the cards: fixed above them, they could
                // leave a phone in landscape or at large text a sliver for the list.
                Results(
                    state,
                    SuggestionCardActions(onOpen, viewModel::addMissingToList, viewModel::toggleSaved),
                    onRefresh = viewModel::generate,
                    onGoToPantry = onGoToPantry,
                    onBrowseEarlier = viewModel::browseEarlier,
                    header = {
                        item(key = OPTIONS_KEY) { Options(state, viewModel) }
                        if (!state.isOnline) item(key = OFFLINE_KEY) { OfflineNote() }
                        if (state.optionsChanged && !state.isGenerating && state.isOnline) {
                            item(key = FILTERS_CHANGED_KEY) { FiltersChangedBanner(onUpdate = viewModel::generate) }
                        }
                        if (state.isGenerating) {
                            item(key = GENERATING_KEY) { GeneratingStrip(onCancel = viewModel::cancelGeneration) }
                        }
                        state.error?.let { message ->
                            item(key = ERROR_KEY) { SuggestionsErrorBanner(message, viewModel::retry, state.isOnline) }
                        }
                    },
                )
            }
        }
    }
}

/** Ideas and saved recipes one tap apart, with the count so the kept ones are never out of sight. */
@Composable
private fun ViewSwitch(
    showingSaved: Boolean,
    savedCount: Int,
    onShowSaved: (Boolean) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !showingSaved,
            onClick = { onShowSaved(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(Res.string.suggestions_view_ideas)) }
        SegmentedButton(
            selected = showingSaved,
            onClick = { onShowSaved(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(Res.string.suggestions_view_saved, savedCount)) }
    }
}

/** An unsave offers Undo, which saves the same recipe again; everything else is one sentence. */
private suspend fun SnackbarHostState.announce(
    event: SuggestionsEvent,
    onUndoUnsave: (Recipe) -> Unit,
) {
    if (event !is SuggestionsEvent.Unsaved) {
        showSnackbar(event.sentence().text())
        return
    }
    // Bounded: with an action, Material's default is Indefinite and the Undo would never go away.
    val outcome =
        showSnackbar(
            message = event.sentence().text(),
            actionLabel = getString(Res.string.detail_undo),
            duration = SnackbarDuration.Long,
        )
    if (outcome == SnackbarResult.ActionPerformed) onUndoUnsave(event.restore)
}

/** Always on screen. Mid-run it only looks disabled: the run is stopped from the strip's Cancel. */
@Composable
private fun NewIdeasButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = if (enabled) Modifier else Modifier.semantics { disabled() },
        containerColor = if (enabled) colors.primaryContainer else colors.surfaceContainerHighest,
        contentColor = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant,
        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
        text = { Text(stringResource(Res.string.suggestions_new_ideas)) },
    )
}

/** Separate from the collector so the wording can be tested: there is no Compose harness here. */
internal fun SuggestionsEvent.sentence(): UiText =
    when (this) {
        is SuggestionsEvent.AddedToList ->
            UiText.Joined(
                Res.string.snack_counts_joined,
                listOf(
                    UiText.Plural(Res.plurals.snack_added_count, added),
                    UiText.Plural(Res.plurals.snack_not_needed_count, skipped),
                ),
            )
        is SuggestionsEvent.Failed -> message
        SuggestionsEvent.Saved -> UiText.of(Res.string.snack_saved)
        is SuggestionsEvent.Unsaved -> UiText.of(Res.string.snack_unsaved)
    }

@Composable
private fun Options(
    state: SuggestionsUiState,
    viewModel: SuggestionsViewModel,
) {
    // Scrolls: at large font sizes or in a longer language the second chip no longer fits.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
        item {
            FilterChip(
                selected = state.options.useOnlyPantry,
                onClick = { viewModel.setUseOnlyPantry(!state.options.useOnlyPantry) },
                label = { Text(stringResource(Res.string.suggestions_only_pantry)) },
            )
        }
        item {
            FilterChip(
                selected = state.options.maxMinutes != null,
                onClick = { viewModel.setMaxMinutes(if (state.options.maxMinutes == null) QUICK_MINUTES else null) },
                label = { Text(stringResource(Res.string.suggestions_quick)) },
            )
        }
        item {
            FilterChip(
                selected = state.options.useExpiringSoon,
                onClick = { viewModel.setUseExpiringSoon(!state.options.useExpiringSoon) },
                label = { Text(stringResource(Res.string.suggestions_use_expiring)) },
            )
        }
        // One meal at most: tapping the chosen one again means any meal.
        items(MealType.entries) { meal ->
            val selected = state.options.mealType == meal
            FilterChip(
                selected = selected,
                onClick = { viewModel.setMealType(if (selected) null else meal) },
                label = { Text(stringResource(meal.label())) },
            )
        }
    }
}

private fun MealType.label(): StringResource =
    when (this) {
        MealType.BREAKFAST -> Res.string.suggestions_meal_breakfast
        MealType.LUNCH -> Res.string.suggestions_meal_lunch
        MealType.DINNER -> Res.string.suggestions_meal_dinner
        MealType.SNACK -> Res.string.suggestions_meal_snack
    }

@Composable
private fun Results(
    state: SuggestionsUiState,
    actions: SuggestionCardActions,
    onRefresh: () -> Unit,
    onGoToPantry: () -> Unit,
    onBrowseEarlier: (Int?) -> Unit,
    header: LazyListScope.() -> Unit,
) {
    // Pulling down is the secondary trigger for the button's own generate(), guarded the same
    // way. The strip above shows progress, so the pull indicator is not held open for it.
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
            contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
        ) {
            header()
            if (state.earlier.isNotEmpty()) {
                item(key = EARLIER_ROW_KEY) { EarlierIdeasRow(state.earlier, state.browsingEarlier, onBrowseEarlier) }
            }
            val shown = state.shownSuggestions
            when {
                // Before isGenerating: a stored set from the last launch stays on screen while a
                // new one runs behind it, rather than being hidden by a skeleton for the better
                // part of a minute.
                shown.isNotEmpty() ->
                    items(shown, key = { it.id.value }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            saved = suggestion.id in state.savedIds,
                            actions = actions,
                            addingMissing = state.addingMissingFor != null,
                            modifier = Modifier.animateItem(),
                        )
                    }
                // A skeleton rather than a spinner: the call takes the better part of a minute,
                // and a blank screen for that long reads as a hang rather than as work.
                state.isGenerating -> item { Skeleton() }
                // A failed run is reported by the banner above; anything else empty says why it is.
                state.hasGenerated && state.error != null -> Unit
                else -> item { EmptyResults(state, onRefresh, onGoToPantry, Modifier.fillParentMaxSize()) }
            }
        }
    }
}

/** Found nothing, never asked, or never asked because nothing is held: each has its own way forward. */
@Composable
private fun EmptyResults(
    state: SuggestionsUiState,
    onRefresh: () -> Unit,
    onGoToPantry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.hasGenerated ->
            EmptyState(
                title = stringResource(Res.string.suggestions_nothing_title),
                body = stringResource(Res.string.suggestions_nothing_body_action),
                modifier = modifier,
                icon = Icons.Outlined.Kitchen,
                actionLabel = stringResource(Res.string.suggestions_add_to_pantry),
                onAction = onGoToPantry,
            )
        // Nothing ran on arrival because nothing is held: filling the pantry comes first.
        state.isPantryEmpty ->
            EmptyState(
                title = stringResource(Res.string.suggestions_pantry_empty_title),
                body = stringResource(Res.string.suggestions_pantry_empty_body),
                modifier = modifier,
                icon = Icons.Outlined.Kitchen,
                actionLabel = stringResource(Res.string.suggestions_add_to_pantry),
                onAction = onGoToPantry,
            )
        else ->
            EmptyState(
                title = stringResource(Res.string.suggestions_empty_title),
                body = stringResource(Res.string.suggestions_empty_body_action),
                modifier = modifier,
                icon = Icons.Filled.AutoAwesome,
                actionLabel = stringResource(Res.string.suggestions_new_ideas),
                onAction = onRefresh,
            )
    }
}

@Composable
private fun Skeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.medium)) {
        repeat(SKELETON_CARDS) { SkeletonCard() }
    }
}

/**
 * The same shape as [SuggestionCard] — the placeholder image, a title bar, two summary lines and
 * a coverage bar — so the layout does not jump once real cards replace it.
 */
@Composable
private fun SkeletonCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = tonalCardColors()) {
        Column {
            RecipeImagePlaceholder()
            Column(
                modifier = Modifier.padding(Dimens.large),
                verticalArrangement = Arrangement.spacedBy(Dimens.small),
            ) {
                SkeletonBar(Modifier.fillMaxWidth(SKELETON_TITLE_WIDTH).height(Dimens.large))
                SkeletonBar(Modifier.fillMaxWidth().height(Dimens.medium))
                SkeletonBar(Modifier.fillMaxWidth(SKELETON_SUMMARY_WIDTH).height(Dimens.medium))
                CoverageBar(fraction = 0f)
            }
        }
    }
}

/** From Pantry's Use soon: the filter is chosen and the ideas view shown; generating stays a tap. */
@Composable
private fun UseExpiringRequests(viewModel: SuggestionsViewModel) {
    val requests = koinInject<IdeasRequests>()
    LaunchedEffect(requests) {
        requests.useExpiring.collect {
            viewModel.showSaved(false)
            viewModel.setUseExpiringSoon(true)
        }
    }
}
