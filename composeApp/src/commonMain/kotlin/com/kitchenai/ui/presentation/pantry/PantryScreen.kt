package com.kitchenai.ui.presentation.pantry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.Freshness
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.component.SwipeToDismissRow
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.presentation.common.text
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_add
import com.kitchenai.ui.resources.pantry_empty_body
import com.kitchenai.ui.resources.pantry_empty_title
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

    LaunchedEffect(userId) { viewModel.start(userId, platformLanguageTags()) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event -> snackbar.announce(event, viewModel::undoRemove) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openEditor(null) },
            ) { Text(stringResource(Res.string.pantry_add)) }
        },
    ) { padding ->
        PantryList(
            state = state,
            onEdit = viewModel::openEditor,
            onRemove = viewModel::remove,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    if (state.isEditorOpen) {
        PantryItemEditor(
            state = state,
            onDismiss = viewModel::closeEditor,
            onSubmit = viewModel::save,
        )
    }
}

@Composable
private fun PantryList(
    state: PantryUiState,
    onEdit: (PantryItemUi) -> Unit,
    onRemove: (PantryItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = state.error
    // Resolved before the grouping: section() reads the catalogue's language, and remember's
    // block is not composition.
    val titles = sectionTitles()
    val sections =
        remember(state.items, titles) { state.items.groupBy { item -> titles.of(item.freshness) } }

    when {
        state.isLoading -> LoadingState(modifier)
        state.items.isEmpty() && error != null -> ErrorState(message = error.resolve(), modifier = modifier)
        state.items.isEmpty() ->
            EmptyState(
                title = stringResource(Res.string.pantry_empty_title),
                body = stringResource(Res.string.pantry_empty_body),
                modifier = modifier,
            )
        else ->
            LazyColumn(modifier = modifier) {
                // A failed listener keeps the last good list underneath it: it stopped emitting,
                // it did not report an empty pantry.
                // Keyed on a constant rather than on error itself: LazyListScope.item requires a
                // Bundle-saveable key, and UiText wraps a StringResource that is not one. There is
                // at most one error item here, so a fixed key is exact rather than a workaround.
                if (error != null) item(key = "pantry-error") { ErrorState(message = error.resolve()) }
                sections.forEach { (title, rows) ->
                    item(key = title) { SectionHeader(title = title) }
                    items(rows, key = { row -> row.id.value }) { row ->
                        PantryRow(item = row, onEdit = onEdit, onRemove = onRemove)
                    }
                }
            }
    }
}

@Composable
private fun PantryRow(
    item: PantryItemUi,
    onEdit: (PantryItemUi) -> Unit,
    onRemove: (PantryItemUi) -> Unit,
) {
    SwipeToDismissRow(onDismiss = { onRemove(item) }) {
        ListItem(
            headlineContent = { Text(item.name) },
            supportingContent = { Text(item.quantityLabel) },
            trailingContent = { item.locationLabel?.let { label -> Text(label) } },
            modifier = Modifier.clickable { onEdit(item) },
        )
    }
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
 * uses them is not: `ObservePantry` already sorts what runs out first to the top, so grouping in
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
