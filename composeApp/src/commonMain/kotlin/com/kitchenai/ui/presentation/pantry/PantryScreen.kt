package com.kitchenai.ui.presentation.pantry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.Freshness
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.ErrorBanner
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.component.SwipeToDismissRow
import com.kitchenai.ui.designsystem.component.Tag
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.presentation.common.text
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_retry
import com.kitchenai.ui.resources.not_in_catalogue
import com.kitchenai.ui.resources.pantry_add
import com.kitchenai.ui.resources.pantry_add_named
import com.kitchenai.ui.resources.pantry_delete
import com.kitchenai.ui.resources.pantry_empty_action
import com.kitchenai.ui.resources.pantry_empty_body
import com.kitchenai.ui.resources.pantry_empty_title
import com.kitchenai.ui.resources.pantry_no_matches
import com.kitchenai.ui.resources.pantry_no_matches_location
import com.kitchenai.ui.resources.pantry_removed
import com.kitchenai.ui.resources.pantry_section_expired
import com.kitchenai.ui.resources.pantry_section_fresh
import com.kitchenai.ui.resources.pantry_section_soon
import com.kitchenai.ui.resources.pantry_section_undated
import com.kitchenai.ui.resources.pantry_undo
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// The screen owns its wording: the navigation entry is one line and has nowhere to put it.

/**
 * The inventory, maintained standing up and with one hand: adding is one tap away and no action
 * waits for the network before the list moves.
 */
@Composable
fun PantryScreen(
    userId: UserId,
    modifier: Modifier = Modifier,
    viewModel: PantryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(userId) { viewModel.start(userId, platformLanguageTags()) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event -> snackbar.announce(event, viewModel::undoRemove) }
    }

    Scaffold(
        modifier = modifier,
        // The insets are AppShell's: its own Scaffold already sizes the content area to sit
        // between the toolbar and the bottom bar, and a second round here would open a gap on
        // both edges rather than closing one.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Shrinks to its icon while scrolling down so it covers less of the rows being read;
            // the icon carries the label only then, or a screen reader would say it twice.
            // With nothing listed there is nothing to scroll back up to: stay expanded.
            val expanded = listState.isScrollingUp() || state.visible.isEmpty()
            val addLabel = stringResource(Res.string.pantry_add)
            ExtendedFloatingActionButton(
                onClick = { viewModel.openEditor(null) },
                expanded = expanded,
                icon = { Icon(Icons.Filled.Add, contentDescription = if (expanded) null else addLabel) },
                text = { Text(addLabel) },
            )
        },
    ) { padding ->
        PantryList(
            state = state,
            listState = listState,
            actions =
                PantryListActions(
                    onEdit = viewModel::openEditor,
                    onRemove = viewModel::remove,
                    onQuery = viewModel::search,
                    onLocation = viewModel::filterByLocation,
                    onAddTyped = viewModel::addFromSearch,
                    onAdd = { viewModel.openEditor(null) },
                    onRetry = viewModel::retry,
                ),
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    if (state.isEditorOpen) {
        PantryItemEditor(
            state = state,
            onDismiss = viewModel::closeEditor,
            onSubmit = viewModel::save,
            onSubmitAndAddAnother = viewModel::saveAndAddAnother,
            onDelete = viewModel::deleteEditing,
        )
    }
}

/** What the list hands back to the ViewModel, grouped so the list does not take seven lambdas. */
private class PantryListActions(
    val onEdit: (PantryItemUi) -> Unit,
    val onRemove: (PantryItemUi) -> Unit,
    val onQuery: (String) -> Unit,
    val onLocation: (TermRef?) -> Unit,
    val onAddTyped: () -> Unit,
    val onAdd: () -> Unit,
    val onRetry: () -> Unit,
)

@Composable
private fun PantryList(
    state: PantryUiState,
    listState: LazyListState,
    actions: PantryListActions,
    modifier: Modifier = Modifier,
) {
    val error = state.error

    when {
        state.isLoading -> LoadingState(modifier)
        state.items.isEmpty() && error != null ->
            ErrorState(
                message = error.resolve(),
                modifier = modifier,
                retryLabel = stringResource(Res.string.error_retry),
                onRetry = actions.onRetry,
            )
        state.items.isEmpty() ->
            EmptyState(
                title = stringResource(Res.string.pantry_empty_title),
                body = stringResource(Res.string.pantry_empty_body),
                modifier = modifier,
                icon = Icons.Outlined.Kitchen,
                actionLabel = stringResource(Res.string.pantry_empty_action),
                onAction = actions.onAdd,
            )
        else ->
            Column(modifier = modifier) {
                PantrySearchBar(
                    query = state.query,
                    onQuery = actions.onQuery,
                    locations = state.filterLocations,
                    selected = state.locationFilter,
                    onLocation = actions.onLocation,
                    modifier = Modifier.padding(vertical = Dimens.small),
                )
                if (state.visible.isEmpty()) {
                    // The rows' list carries the banner otherwise; a filter that empties it must not hide it.
                    error?.let { message -> PantryErrorBanner(message, actions.onRetry) }
                    NoMatches(query = state.query, onAddTyped = actions.onAddTyped)
                } else {
                    SectionedRows(state, listState, actions)
                }
            }
    }
}

/** Over rows that are still there: the listener stopped, it did not empty the pantry. */
@Composable
private fun PantryErrorBanner(
    message: UiText,
    onRetry: () -> Unit,
) {
    ErrorBanner(
        message = message.resolve(),
        modifier = Modifier.padding(horizontal = Dimens.large, vertical = Dimens.small),
        retryLabel = stringResource(Res.string.error_retry),
        onRetry = onRetry,
    )
}

@Composable
private fun SectionedRows(
    state: PantryUiState,
    listState: LazyListState,
    actions: PantryListActions,
) {
    val error = state.error
    // Resolved before the grouping: section() reads the catalogue's language, and remember's
    // block is not composition.
    val titles = sectionTitles()
    val sections =
        remember(state.visible, titles) { state.visible.groupBy { item -> titles.of(item.freshness) } }
    // The bottom padding keeps the last row clear of the floating button.
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = Dimens.fabClearance)) {
        // A failed listener keeps the last good list underneath it: it stopped emitting,
        // it did not report an empty pantry.
        // Keyed on a constant rather than on error itself: LazyListScope.item requires a
        // Bundle-saveable key, and UiText wraps a StringResource that is not one. There is
        // at most one error item here, so a fixed key is exact rather than a workaround.
        if (error != null) item(key = "pantry-error") { PantryErrorBanner(error, actions.onRetry) }
        sections.forEach { (title, rows) ->
            item(key = title) {
                SectionHeader(title = title, trailing = { SectionCount(rows.size) }, modifier = Modifier.animateItem())
            }
            items(rows, key = { row -> row.id.value }) { row ->
                PantryRow(
                    item = row,
                    onEdit = actions.onEdit,
                    onRemove = actions.onRemove,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** A search that found nothing offers to add what was typed rather than ending in a blank screen. */
@Composable
private fun NoMatches(
    query: String,
    onAddTyped: () -> Unit,
) {
    val typed = query.trim()
    Column(
        modifier = Modifier.fillMaxWidth().padding(Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                if (typed.isEmpty()) {
                    stringResource(Res.string.pantry_no_matches_location)
                } else {
                    stringResource(Res.string.pantry_no_matches, typed)
                },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (typed.isNotEmpty()) {
            Button(onClick = onAddTyped) { Text(stringResource(Res.string.pantry_add_named, typed)) }
        }
    }
}

/** Whether the list is moving up (or still): the floating button stays expanded then. */
@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            val up =
                if (previousIndex != firstVisibleItemIndex) {
                    previousIndex > firstVisibleItemIndex
                } else {
                    previousOffset >= firstVisibleItemScrollOffset
                }
            previousIndex = firstVisibleItemIndex
            previousOffset = firstVisibleItemScrollOffset
            up
        }
    }.value
}

@Composable
private fun PantryRow(
    item: PantryItemUi,
    onEdit: (PantryItemUi) -> Unit,
    onRemove: (PantryItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Italics alone tell the two kinds of row apart only to someone who can see them.
    val freeTextMarker: (@Composable () -> Unit)? =
        if (item.ingredient == null) {
            { Text(stringResource(Res.string.not_in_catalogue)) }
        } else {
            null
        }
    SwipeToDismissRow(
        onDismiss = { onRemove(item) },
        deleteLabel = stringResource(Res.string.pantry_delete),
        modifier = modifier,
    ) { deleteAction ->
        ListItem(
            overlineContent = freeTextMarker,
            headlineContent = {
                // Set apart from a catalogue row, which is the row that merges and that the
                // pantry matcher can reason about; this one is neither.
                Text(item.name, fontStyle = if (item.ingredient != null) FontStyle.Normal else FontStyle.Italic)
            },
            supportingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.quantityLabel)
                    item.expiryLabel?.let { label -> ExpiryTag(label.resolve(), item.freshness) }
                }
            },
            trailingContent = { item.locationLabel?.let { label -> Text(label) } },
            modifier = deleteAction.clickable { onEdit(item) },
        )
    }
}

@Composable
private fun SectionCount(count: Int) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Coloured by urgency so the eye finds what to use first without reading every row. */
@Composable
private fun ExpiryTag(
    label: String,
    freshness: Freshness,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (freshness) {
            Freshness.Expired -> colors.errorContainer to colors.onErrorContainer
            is Freshness.ExpiringSoon -> colors.tertiaryContainer to colors.onTertiaryContainer
            Freshness.Fresh, Freshness.Unknown -> colors.surfaceVariant to colors.onSurfaceVariant
        }
    Tag(label = label, containerColor = container, contentColor = content)
}

private suspend fun SnackbarHostState.announce(
    event: PantryEvent,
    onUndo: (PantryItem) -> Unit,
) {
    when (event) {
        is PantryEvent.ItemRemoved -> {
            val outcome =
                showSnackbar(
                    message = "${getString(Res.string.pantry_removed)}: ${event.name}",
                    actionLabel = getString(Res.string.pantry_undo),
                )
            if (outcome == SnackbarResult.ActionPerformed) onUndo(event.restore)
        }

        is PantryEvent.SaveFailed -> showSnackbar(message = event.message.text())
    }
}

/**
 * The four section names a row can fall under. Read once, in composition, so the grouping that
 * uses them is not: `ObservePantryUseCase` already sorts what runs out first to the top, so grouping in
 * encounter order needs no comparator here either.
 *
 * A data class on purpose. `remember` keys on this, and with identity equality a fresh instance
 * per recomposition would invalidate the cache every time — which is the memoisation the caller
 * asks for, silently not happening.
 */
private data class SectionTitles(
    val expired: String,
    val soon: String,
    val fresh: String,
    val undated: String,
) {
    fun of(freshness: Freshness): String =
        when (freshness) {
            Freshness.Expired -> expired
            is Freshness.ExpiringSoon -> soon
            Freshness.Fresh -> fresh
            Freshness.Unknown -> undated
        }
}

@Composable
private fun sectionTitles(): SectionTitles =
    SectionTitles(
        expired = stringResource(Res.string.pantry_section_expired),
        soon = stringResource(Res.string.pantry_section_soon),
        fresh = stringResource(Res.string.pantry_section_fresh),
        undated = stringResource(Res.string.pantry_section_undated),
    )
