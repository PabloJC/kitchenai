package com.kitchenai.ui.presentation.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.component.SwipeToDismissRow
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformLanguageTags
import org.koin.compose.viewmodel.koinViewModel

/**
 * The list as it is used: one hand, bad signal, a phone at chest height. Nothing here waits for a
 * round trip — every gesture writes and the stream redraws whatever comes back.
 */
@Composable
fun ShoppingScreen(
    userId: UserId,
    modifier: Modifier = Modifier,
    viewModel: ShoppingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmingClear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(userId) { viewModel.start(userId, platformLanguageTags(), DEFAULT_LIST_NAME) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event -> snackbar.announce(event, viewModel::undoRemove) }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            SectionHeader(title = state.listName)
            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
                )
            }

            when {
                state.isLoading -> LoadingState(Modifier.weight(1f))
                // The banner above already carries the reason; this only avoids claiming the
                // list is empty when it never loaded.
                state.failedToLoad ->
                    EmptyState(title = FAILED_TITLE, body = FAILED_BODY, modifier = Modifier.weight(1f))

                state.unchecked.isEmpty() && state.checked.isEmpty() ->
                    EmptyState(title = EMPTY_TITLE, body = EMPTY_BODY, modifier = Modifier.weight(1f))
                else ->
                    ShoppingItems(
                        state = state,
                        onCheck = viewModel::setChecked,
                        onRemove = viewModel::remove,
                        onClearChecked = { confirmingClear = true },
                        modifier = Modifier.weight(1f),
                    )
            }

            ShoppingAddField(
                draft = state.draft,
                onDraftChange = viewModel::onDraftChange,
                onPick = viewModel::onPick,
                onAdd = viewModel::add,
            )
        }
    }

    if (confirmingClear) {
        ClearCheckedDialog(
            onConfirm = {
                confirmingClear = false
                viewModel.clearChecked()
            },
            onDismiss = { confirmingClear = false },
        )
    }
}

@Composable
private fun ShoppingItems(
    state: ShoppingUiState,
    onCheck: (ShoppingItemId, Boolean) -> Unit,
    onRemove: (ShoppingItemId) -> Unit,
    onClearChecked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item { SectionHeader(title = TO_BUY_TITLE) }
        items(state.unchecked, key = { line -> line.id.value }) { line ->
            ShoppingItemRow(
                item = line,
                onCheck = { checked -> onCheck(line.id, checked) },
                onRemove = { onRemove(line.id) },
            )
        }

        if (state.checked.isNotEmpty()) {
            item {
                SectionHeader(
                    title = IN_CART_TITLE,
                    trailing = { TextButton(onClick = onClearChecked) { Text(CLEAR_LABEL) } },
                )
            }
            items(state.checked, key = { line -> line.id.value }) { line ->
                ShoppingItemRow(
                    item = line,
                    onCheck = { checked -> onCheck(line.id, checked) },
                    onRemove = { onRemove(line.id) },
                )
            }
        }
    }
}

/** The whole row is the checkbox: a 48dp target is what a thumb hits while pushing a trolley. */
@Composable
private fun ShoppingItemRow(
    item: ShoppingItemUi,
    onCheck: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToDismissRow(onDismiss = onRemove, modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget)
                    .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = onCheck)
                    .padding(horizontal = Dimens.large, vertical = Dimens.small),
            horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.checked, onCheckedChange = null)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    // Free text is set apart from a catalogue line, which is the line that merges.
                    fontStyle = if (item.fromCatalogue) FontStyle.Normal else FontStyle.Italic,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                )
            }

            item.quantity?.let { quantity ->
                Text(text = quantity, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ClearCheckedDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(CLEAR_TITLE) },
        text = { Text(CLEAR_BODY) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(CLEAR_LABEL) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(CANCEL_LABEL) } },
    )
}

/** The undo lives on the snackbar and nowhere else: the state must not remember a removed line. */
private suspend fun SnackbarHostState.announce(
    event: ShoppingEvent,
    onUndo: (ShoppingItem) -> Unit,
) {
    when (event) {
        is ShoppingEvent.ItemRemoved -> {
            val undone = showSnackbar("${event.label} $REMOVED_SUFFIX", UNDO_LABEL) == SnackbarResult.ActionPerformed
            if (undone) onUndo(event.restore)
        }

        is ShoppingEvent.CheckedCleared -> showSnackbar("${event.count} $CLEARED_SUFFIX")
    }
}

// The screen owns its own wording; every component it draws takes each string as a parameter.
// This one is not private: the detail screen can reach the same list, and two spellings of the
// name would mean two names depending on which screen got there first.
internal const val DEFAULT_LIST_NAME = "Shopping list"
private const val TO_BUY_TITLE = "To buy"
private const val IN_CART_TITLE = "In the cart"
private const val CLEAR_LABEL = "Clear"
private const val CANCEL_LABEL = "Cancel"
private const val CLEAR_TITLE = "Clear what is in the cart?"
private const val CLEAR_BODY = "The ticked lines are removed from the list on every device."
private const val FAILED_TITLE = "The list did not load"
private const val FAILED_BODY = "Check the message above and try again"
private const val EMPTY_TITLE = "Nothing to buy"
private const val EMPTY_BODY = "Add what you need and tick it off as you go."
private const val REMOVED_SUFFIX = "removed"
private const val CLEARED_SUFFIX = "lines cleared"
private const val UNDO_LABEL = "Undo"
