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
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.shopping_cancel
import com.kitchenai.ui.resources.shopping_clear
import com.kitchenai.ui.resources.shopping_clear_body
import com.kitchenai.ui.resources.shopping_clear_title
import com.kitchenai.ui.resources.shopping_cleared_suffix
import com.kitchenai.ui.resources.shopping_default_list
import com.kitchenai.ui.resources.shopping_empty_body
import com.kitchenai.ui.resources.shopping_empty_title
import com.kitchenai.ui.resources.shopping_failed_body
import com.kitchenai.ui.resources.shopping_failed_title
import com.kitchenai.ui.resources.shopping_in_cart
import com.kitchenai.ui.resources.shopping_removed_suffix
import com.kitchenai.ui.resources.shopping_to_buy
import com.kitchenai.ui.resources.shopping_undo
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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

    val defaultListName = stringResource(Res.string.shopping_default_list)

    LaunchedEffect(userId) { viewModel.start(userId, platformLanguageTags(), defaultListName) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event -> snackbar.announce(event, viewModel::undoRemove) }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            SectionHeader(title = state.listName)
            state.error?.let { message -> ErrorBanner(message) }

            when {
                state.isLoading -> LoadingState(Modifier.weight(1f))
                // The banner above already carries the reason; this only avoids claiming the
                // list is empty when it never loaded.
                state.failedToLoad ->
                    EmptyState(
                        title = stringResource(Res.string.shopping_failed_title),
                        body = stringResource(Res.string.shopping_failed_body),
                        modifier = Modifier.weight(1f),
                    )

                state.unchecked.isEmpty() && state.checked.isEmpty() ->
                    EmptyState(
                        title = stringResource(Res.string.shopping_empty_title),
                        body = stringResource(Res.string.shopping_empty_body),
                        modifier = Modifier.weight(1f),
                    )
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
        item { SectionHeader(title = stringResource(Res.string.shopping_to_buy)) }
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
                    title = stringResource(Res.string.shopping_in_cart),
                    trailing = {
                        TextButton(
                            onClick = onClearChecked,
                        ) { Text(stringResource(Res.string.shopping_clear)) }
                    },
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
        title = { Text(stringResource(Res.string.shopping_clear_title)) },
        text = { Text(stringResource(Res.string.shopping_clear_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.shopping_clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.shopping_cancel)) } },
    )
}

/** Above the list rather than replacing it: a failed listener leaves the last good lines standing. */
@Composable
private fun ErrorBanner(message: UiText) {
    Text(
        text = message.resolve(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
    )
}

/** The undo lives on the snackbar and nowhere else: the state must not remember a removed line. */
private suspend fun SnackbarHostState.announce(
    event: ShoppingEvent,
    onUndo: (ShoppingItem) -> Unit,
) {
    when (event) {
        is ShoppingEvent.ItemRemoved -> {
            val undone =
                showSnackbar(
                    "${event.label} ${getString(Res.string.shopping_removed_suffix)}",
                    getString(Res.string.shopping_undo),
                ) == SnackbarResult.ActionPerformed
            if (undone) onUndo(event.restore)
        }

        is ShoppingEvent.CheckedCleared ->
            showSnackbar("${event.count} ${getString(Res.string.shopping_cleared_suffix)}")
    }
}

// The screen owns its own wording; every component it draws takes each string as a parameter.
// This one is not private: the detail screen can reach the same list, and two spellings of the
// name would mean two names depending on which screen got there first.
