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
import org.koin.compose.viewmodel.koinViewModel

// The screen owns its wording: the navigation entry is one line and has nowhere to put it.
private const val ADD_LABEL = "Add"
private const val UNDO_LABEL = "Undo"
private const val REMOVED_LABEL = "Removed"
private const val EMPTY_TITLE = "The pantry is empty"
private const val EMPTY_BODY = "Add what you already have and the suggestions start working"
private const val EXPIRED_SECTION = "Expired"
private const val EXPIRING_SOON_SECTION = "Use soon"
private const val FRESH_SECTION = "Fresh"
private const val UNDATED_SECTION = "No expiry date"

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
            FloatingActionButton(onClick = { viewModel.openEditor(null) }) { Text(ADD_LABEL) }
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
    val sections = remember(state.items) { state.items.groupBy { item -> item.freshness.section() } }

    when {
        state.isLoading -> LoadingState(modifier)
        state.items.isEmpty() && error != null -> ErrorState(message = error.resolve(), modifier = modifier)
        state.items.isEmpty() -> EmptyState(title = EMPTY_TITLE, body = EMPTY_BODY, modifier = modifier)
        else ->
            LazyColumn(modifier = modifier) {
                // A failed listener keeps the last good list underneath it: it stopped emitting,
                // it did not report an empty pantry.
                if (error != null) item(key = error) { ErrorState(message = error.resolve()) }
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
            val outcome = showSnackbar(message = "$REMOVED_LABEL: ${event.name}", actionLabel = UNDO_LABEL)
            if (outcome == SnackbarResult.ActionPerformed) onUndo(event.restore)
        }

        is PantryEvent.SaveFailed -> showSnackbar(message = event.message.text())
    }
}

/**
 * The section a row belongs to. `ObservePantry` already sorts what runs out first to the top, so
 * grouping in encounter order needs no comparator here.
 */
private fun Freshness.section(): String =
    when (this) {
        Freshness.Expired -> EXPIRED_SECTION
        is Freshness.ExpiringSoon -> EXPIRING_SOON_SECTION
        Freshness.Fresh -> FRESH_SECTION
        Freshness.Unknown -> UNDATED_SECTION
    }
