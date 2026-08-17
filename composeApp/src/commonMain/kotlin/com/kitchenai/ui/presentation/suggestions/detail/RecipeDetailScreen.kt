package com.kitchenai.ui.presentation.suggestions.detail

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
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.presentation.common.text
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_add_missing
import com.kitchenai.ui.resources.detail_cancel
import com.kitchenai.ui.resources.detail_cook
import com.kitchenai.ui.resources.detail_cook_body
import com.kitchenai.ui.resources.detail_cook_confirm
import com.kitchenai.ui.resources.detail_cook_title
import com.kitchenai.ui.resources.detail_have
import com.kitchenai.ui.resources.detail_missing
import com.kitchenai.ui.resources.detail_optional_suffix
import com.kitchenai.ui.resources.detail_save
import com.kitchenai.ui.resources.detail_saved
import com.kitchenai.ui.resources.detail_servings
import com.kitchenai.ui.resources.detail_steps
import com.kitchenai.ui.resources.detail_unverifiable
import com.kitchenai.ui.resources.detail_unverifiable_body
import com.kitchenai.ui.resources.shopping_default_list
import com.kitchenai.ui.resources.snack_added_to_list
import com.kitchenai.ui.resources.snack_cooked
import com.kitchenai.ui.resources.snack_saved
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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

    // The same name the shopping screen would create the list under, read here because a
    // LaunchedEffect block is a coroutine and not composition.
    val defaultListName = stringResource(Res.string.shopping_default_list)

    LaunchedEffect(userId, recipeId) {
        viewModel.start(userId, recipeId, platformLanguageTags(), defaultListName)
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
            state.error?.let { message -> Text(message.resolve(), color = MaterialTheme.colorScheme.error) }
            Lines(stringResource(Res.string.detail_have), state.held)
            Lines(stringResource(Res.string.detail_missing), state.missing)
            // Its own section with its own sentence: this is not "missing", and a reader who
            // took it for missing would go shopping for something they may already own.
            if (state.unverifiable.isNotEmpty()) {
                SectionHeader(title = stringResource(Res.string.detail_unverifiable))
                Text(stringResource(Res.string.detail_unverifiable_body), style = MaterialTheme.typography.bodySmall)
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
        Text(stringResource(Res.string.detail_servings), style = MaterialTheme.typography.labelSmall)
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
        Text(line.name + if (line.optional) stringResource(Res.string.detail_optional_suffix) else "")
        // Absent rather than zero: a line with no amount is "to taste", and inventing one for
        // it would be a number the recipe never gave.
        line.quantity?.let { amount -> Text(amount, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Steps(steps: List<String>) {
    if (steps.isEmpty()) return
    SectionHeader(title = stringResource(Res.string.detail_steps))
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
            Text(if (state.isSaved) stringResource(Res.string.detail_saved) else stringResource(Res.string.detail_save))
        }
        OutlinedButton(
            onClick = viewModel::addMissingToList,
            enabled = !state.isWorking && state.missing.isNotEmpty(),
        ) { Text(stringResource(Res.string.detail_add_missing)) }
        Button(onClick = onCook, enabled = state.canCook) { Text(stringResource(Res.string.detail_cook)) }
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
        title = { Text(stringResource(Res.string.detail_cook_title)) },
        text = { Text(stringResource(Res.string.detail_cook_body)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(Res.string.detail_cook_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.detail_cancel)) } },
    )
}

private suspend fun SnackbarHostState.announce(event: RecipeDetailEvent) = showSnackbar(event.sentence().text())

/**
 * Separate from [announce] so the wording can be tested: there is no Compose test harness here,
 * and the sentence is the part that can be wrong.
 */
internal fun RecipeDetailEvent.sentence(): UiText =
    when (this) {
        // Both counts. What "skipped" means is in the string itself now: a line the pantry
        // covers or one the recipe marks optional, never one already on the list.
        is RecipeDetailEvent.AddedToList -> UiText.of(Res.string.snack_added_to_list, added, skipped)
        RecipeDetailEvent.Cooked -> UiText.of(Res.string.snack_cooked)
        RecipeDetailEvent.Saved -> UiText.of(Res.string.snack_saved)
        is RecipeDetailEvent.Failed -> message
    }
