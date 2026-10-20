package com.kitchenai.ui.presentation.suggestions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.CoverageBar
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.RecipeImage
import com.kitchenai.ui.designsystem.component.RecipeImagePlaceholder
import com.kitchenai.ui.designsystem.component.SKELETON_SUMMARY_WIDTH
import com.kitchenai.ui.designsystem.component.SKELETON_TITLE_WIDTH
import com.kitchenai.ui.designsystem.component.SkeletonBar
import com.kitchenai.ui.designsystem.component.Tag
import com.kitchenai.ui.designsystem.component.tonalCardColors
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.text
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_undo
import com.kitchenai.ui.resources.shopping_default_list
import com.kitchenai.ui.resources.snack_added_count
import com.kitchenai.ui.resources.snack_counts_joined
import com.kitchenai.ui.resources.snack_not_needed_count
import com.kitchenai.ui.resources.snack_unsaved
import com.kitchenai.ui.resources.suggestions_add_missing
import com.kitchenai.ui.resources.suggestions_add_to_pantry
import com.kitchenai.ui.resources.suggestions_check_manually
import com.kitchenai.ui.resources.suggestions_coverage_count
import com.kitchenai.ui.resources.suggestions_empty_body_action
import com.kitchenai.ui.resources.suggestions_empty_title
import com.kitchenai.ui.resources.suggestions_generated
import com.kitchenai.ui.resources.suggestions_minutes
import com.kitchenai.ui.resources.suggestions_missing
import com.kitchenai.ui.resources.suggestions_missing_more
import com.kitchenai.ui.resources.suggestions_new_ideas
import com.kitchenai.ui.resources.suggestions_nothing_body_action
import com.kitchenai.ui.resources.suggestions_nothing_title
import com.kitchenai.ui.resources.suggestions_only_pantry
import com.kitchenai.ui.resources.suggestions_pantry_empty_body
import com.kitchenai.ui.resources.suggestions_pantry_empty_title
import com.kitchenai.ui.resources.suggestions_quick
import com.kitchenai.ui.resources.suggestions_view_ideas
import com.kitchenai.ui.resources.suggestions_view_saved
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val QUICK_MINUTES = 30
private const val SKELETON_CARDS = 3

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
            if (!state.showingSaved) NewIdeasButton(enabled = !state.isGenerating, onClick = viewModel::generate)
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
                Options(state, viewModel)
                if (state.optionsChanged && !state.isGenerating) FiltersChangedBanner(onUpdate = viewModel::generate)
                if (state.isGenerating) GeneratingStrip(onCancel = viewModel::cancelGeneration)
                state.error?.let { message -> SuggestionsErrorCard(message, onRetry = viewModel::generate) }
                Results(
                    state,
                    onOpen,
                    onAddMissing = viewModel::addMissingToList,
                    onRefresh = viewModel::generate,
                    onGoToPantry = onGoToPantry,
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
    }
}

@Composable
private fun Results(
    state: SuggestionsUiState,
    onOpen: (RecipeId) -> Unit,
    onAddMissing: (RecipeId) -> Unit,
    onRefresh: () -> Unit,
    onGoToPantry: () -> Unit,
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
            when {
                // Before isGenerating: a stored set from the last launch stays on screen while a
                // new one runs behind it, rather than being hidden by a skeleton for the better
                // part of a minute.
                state.suggestions.isNotEmpty() ->
                    items(state.suggestions, key = { it.id.value }) { suggestion ->
                        SuggestionCard(suggestion, onOpen, onAddMissing, state.addingMissingFor != null)
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

@Composable
private fun SuggestionCard(
    suggestion: SuggestionUi,
    onOpen: (RecipeId) -> Unit,
    onAddMissing: (RecipeId) -> Unit,
    addingMissing: Boolean,
) {
    // The whole card opens the recipe: a dedicated "Open" button repeated what tapping the card
    // already does everywhere else in the app.
    Card(
        onClick = { onOpen(suggestion.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = tonalCardColors(),
    ) {
        Column {
            RecipeImage(tags = suggestion.tagRefs)
            Column(
                modifier = Modifier.padding(Dimens.large),
                verticalArrangement = Arrangement.spacedBy(Dimens.small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(suggestion.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // The prototype's subtle badge, not the raw agent and model id: which model
                    // wrote a dish is a support detail, not something a reader needs on the card.
                    if (suggestion.provenance != null) {
                        Tag(
                            label = stringResource(Res.string.suggestions_generated),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            icon = Icons.Filled.AutoAwesome,
                        )
                    }
                }
                suggestion.summary?.let { summary ->
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Its own row rather than a bare line lost among the others: the one metadata
                // fact this card has, given the same visual weight the prototype's badge gives it.
                suggestion.totalMinutes?.let { minutes ->
                    Tag(
                        label = stringResource(Res.string.suggestions_minutes, minutes),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SuggestionCoverage(suggestion, onAddMissing, addingMissing)
            }
        }
    }
}

@Composable
private fun SuggestionCoverage(
    suggestion: SuggestionUi,
    onAddMissing: (RecipeId) -> Unit,
    addingMissing: Boolean,
) {
    CoverageBar(fraction = suggestion.coverage) {
        Text(
            stringResource(Res.string.suggestions_coverage_count, suggestion.heldCount, suggestion.totalCount),
            style = MaterialTheme.typography.labelSmall,
        )
    }
    if (suggestion.missing.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                missingSentence(suggestion),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAddMissing(suggestion.id) }, enabled = !addingMissing) {
                Icon(
                    Icons.Outlined.AddShoppingCart,
                    contentDescription = stringResource(Res.string.suggestions_add_missing),
                )
            }
        }
    }
    // Its own line, its own wording. Folding these into "missing" would claim the pantry knows
    // something it does not.
    if (suggestion.unverifiable.isNotEmpty()) {
        Text(
            stringResource(Res.string.suggestions_check_manually, suggestion.unverifiable.joinToString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** At most three names, then a count: a long comma-joined line buried the card's other facts. */
@Composable
private fun missingSentence(suggestion: SuggestionUi): String {
    val shown = stringResource(Res.string.suggestions_missing, suggestion.shownMissing.joinToString())
    val hidden = suggestion.hiddenMissingCount
    if (hidden == 0) return shown
    return "$shown ${pluralStringResource(Res.plurals.suggestions_missing_more, hidden, hidden)}"
}
