package com.kitchenai.ui.presentation.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.shopping.DEFAULT_LIST_NAME
import org.koin.compose.viewmodel.koinViewModel

private const val HAVE_TITLE = "In your pantry"
private const val MISSING_TITLE = "You need to buy"
private const val UNVERIFIABLE_TITLE = "Check manually"
private const val UNVERIFIABLE_BODY = "Your pantry cannot say either way about these"
private const val STEPS_TITLE = "Steps"
private const val SERVINGS_LABEL = "Servings"
private const val ADD_LABEL = "Add missing to list"
private const val COOK_LABEL = "Cook this"
private const val SAVE_LABEL = "Save"
private const val SAVED_LABEL = "Saved"
private const val COOK_DIALOG_TITLE = "Cook this?"
private const val COOK_DIALOG_BODY =
    "These ingredients will be subtracted from your pantry. This cannot be undone."
private const val COOK_CONFIRM = "Cook it"
private const val CANCEL = "Cancel"
private const val OPTIONAL_SUFFIX = " (optional)"

@Composable
fun RecipeDetailScreen(
    userId: UserId,
    recipeId: RecipeId,
    modifier: Modifier = Modifier,
    viewModel: RecipeDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmingCook by remember { mutableStateOf(false) }

    LaunchedEffect(userId, recipeId) {
        viewModel.start(userId, recipeId, platformLanguageTags(), DEFAULT_LIST_NAME)
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { event -> snackbar.announce(event) } }

    if (confirmingCook) {
        CookDialog(
            onConfirm = {
                confirmingCook = false
                viewModel.cook()
            },
            onDismiss = { confirmingCook = false },
        )
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.padding(padding).padding(Dimens.large).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            Header(state, viewModel)
            state.error?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) }
            Lines(HAVE_TITLE, state.held)
            Lines(MISSING_TITLE, state.missing)
            // Its own section with its own sentence: this is not "missing", and a reader who
            // took it for missing would go shopping for something they may already own.
            if (state.unverifiable.isNotEmpty()) {
                SectionHeader(title = UNVERIFIABLE_TITLE)
                Text(UNVERIFIABLE_BODY, style = MaterialTheme.typography.bodySmall)
                state.unverifiable.forEach { line -> LineRow(line) }
            }
            Steps(state.steps)
            Actions(state, viewModel) { confirmingCook = true }
        }
    }
}

@Composable
private fun Header(
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
) {
    Text(state.title, style = MaterialTheme.typography.headlineSmall)
    state.summary?.let { summary -> Text(summary, style = MaterialTheme.typography.bodyMedium) }
    state.totalMinutes?.let { minutes -> Text("$minutes min", style = MaterialTheme.typography.labelSmall) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(SERVINGS_LABEL, style = MaterialTheme.typography.labelSmall)
        OutlinedButton(
            onClick = { viewModel.setServings(state.servings - 1) },
            enabled = state.servings > 1 && !state.isWorking,
        ) { Text("-") }
        Text(state.servings.toString(), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { viewModel.setServings(state.servings + 1) },
            enabled = !state.isWorking,
        ) { Text("+") }
    }
}

@Composable
private fun Lines(
    title: String,
    lines: List<IngredientLineUi>,
) {
    if (lines.isEmpty()) return
    SectionHeader(title = title)
    lines.forEach { line -> LineRow(line) }
}

@Composable
private fun LineRow(line: IngredientLineUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(line.name + if (line.optional) OPTIONAL_SUFFIX else "")
        // Absent rather than zero: a line with no amount is "to taste", and inventing one for
        // it would be a number the recipe never gave.
        line.quantity?.let { amount -> Text(amount, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Steps(steps: List<String>) {
    if (steps.isEmpty()) return
    SectionHeader(title = STEPS_TITLE)
    steps.forEachIndexed { index, step ->
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
            Text("${index + 1}.", style = MaterialTheme.typography.titleMedium)
            Text(step, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Actions(
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
    onCook: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        OutlinedButton(onClick = viewModel::save, enabled = !state.isWorking && !state.isSaved) {
            Text(if (state.isSaved) SAVED_LABEL else SAVE_LABEL)
        }
        OutlinedButton(
            onClick = viewModel::addMissingToList,
            enabled = !state.isWorking && state.missing.isNotEmpty(),
        ) { Text(ADD_LABEL) }
        Button(onClick = onCook, enabled = state.canCook) { Text(COOK_LABEL) }
    }
}

/**
 * Two buttons, not one. The prototype gave this a single acknowledgement, but the action
 * subtracts food and cannot be undone, so there has to be a way out of the dialog.
 */
@Composable
private fun CookDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(COOK_DIALOG_TITLE) },
        text = { Text(COOK_DIALOG_BODY) },
        confirmButton = { Button(onClick = onConfirm) { Text(COOK_CONFIRM) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(CANCEL) } },
    )
}

private suspend fun SnackbarHostState.announce(event: RecipeDetailEvent) {
    val message =
        when (event) {
            // Both counts: "added" alone would hide that some lines were already there.
            is RecipeDetailEvent.AddedToList -> "${event.added} added, ${event.skipped} already on the list"
            RecipeDetailEvent.Cooked -> "Taken out of your pantry"
            RecipeDetailEvent.Saved -> "Saved"
            is RecipeDetailEvent.Failed -> event.message
        }
    showSnackbar(message)
}
