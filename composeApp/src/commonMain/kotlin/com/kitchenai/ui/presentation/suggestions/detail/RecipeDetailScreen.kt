package com.kitchenai.ui.presentation.suggestions.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.RecipeImagePlaceholder
import com.kitchenai.ui.designsystem.component.Tag
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
import com.kitchenai.ui.resources.detail_ingredients
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

    // The insets are AppShell's; a second round here would open a gap between the toolbar and
    // the bottom bar rather than closing one.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        // Pinned rather than the scroll's last row, so both actions are reachable without
        // scrolling past the steps.
        bottomBar = { if (!state.isLoading) Actions(state, viewModel) { confirmingCook = true } },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.large),
        ) {
            Header(state, viewModel)
            state.error?.let { message ->
                Text(
                    message.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Dimens.large),
                )
            }
            IngredientsCard(state)
            if (state.unverifiable.isNotEmpty()) UnverifiableCard(state.unverifiable)
            StepsCard(state.steps)
        }
    }
}

@Composable
private fun Header(
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
) {
    // Edge to edge, unlike everything below it: the slot a photograph will one day fill sits
    // flush with the top of the scroll rather than margined like body content.
    RecipeImagePlaceholder()
    Column(
        modifier = Modifier.padding(horizontal = Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        Text(state.title, style = MaterialTheme.typography.headlineSmall)
        state.summary?.let { summary -> Text(summary, style = MaterialTheme.typography.bodyMedium) }
        state.totalMinutes?.let { minutes -> Text("$minutes min", style = MaterialTheme.typography.labelSmall) }
        if (state.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.extraSmall)) {
                state.tags.forEach { tag ->
                    Tag(
                        label = tag,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        ServingsStepper(state, viewModel)
    }
}

/** One rounded group rather than two loose buttons either side of the count. */
@Composable
private fun ServingsStepper(
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.small, vertical = Dimens.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            Text(stringResource(Res.string.detail_servings), style = MaterialTheme.typography.labelSmall)
            StepperButton("-", enabled = state.servings > 1 && !state.isWorking) {
                viewModel.setServings(state.servings - 1)
            }
            Text(state.servings.toString(), style = MaterialTheme.typography.titleMedium)
            StepperButton("+", enabled = !state.isWorking) { viewModel.setServings(state.servings + 1) }
        }
    }
}

@Composable
private fun StepperButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // The full 48dp touch target, even though the design's own circle reads smaller: below that
    // size the tap is unreliable on a phone held in one hand, and this is the button someone
    // taps repeatedly while their hands are full of food.
    Box(
        modifier =
            Modifier
                .size(Dimens.touchTarget)
                .clip(CircleShape)
                .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun IngredientsCard(state: RecipeDetailUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        Column(
            modifier = Modifier.padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            Text(stringResource(Res.string.detail_ingredients), style = MaterialTheme.typography.titleMedium)
            // One list, not two: a section header per bucket read like a shopping list. The
            // status now travels with each row instead, as a chip.
            state.held.forEach { line -> StatusRow(line, held = true) }
            state.missing.forEach { line -> StatusRow(line, held = false) }
        }
    }
}

@Composable
private fun StatusRow(
    line: IngredientLineUi,
    held: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.extraSmall)) {
            line.quantity?.let { amount ->
                Text(amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(line.name + if (line.optional) stringResource(Res.string.detail_optional_suffix) else "")
        }
        val colors = MaterialTheme.colorScheme
        Tag(
            label = stringResource(if (held) Res.string.detail_have else Res.string.detail_missing),
            containerColor = if (held) colors.secondaryContainer else colors.errorContainer,
            contentColor = if (held) colors.onSecondaryContainer else colors.onErrorContainer,
        )
    }
}

@Composable
private fun UnverifiableCard(lines: List<IngredientLineUi>) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        Column(
            modifier = Modifier.padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            // Its own section with its own sentence: this is not "missing", and a reader who
            // took it for missing would go shopping for something they may already own.
            Text(stringResource(Res.string.detail_unverifiable), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.detail_unverifiable_body), style = MaterialTheme.typography.bodySmall)
            lines.forEach { line ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(line.name + if (line.optional) stringResource(Res.string.detail_optional_suffix) else "")
                    line.quantity?.let { amount -> Text(amount, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun StepsCard(steps: List<String>) {
    if (steps.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        Column(
            modifier = Modifier.padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            Text(stringResource(Res.string.detail_steps), style = MaterialTheme.typography.titleMedium)
            steps.forEachIndexed { index, step -> StepRow(index + 1, step, connecting = index != steps.lastIndex) }
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    step: String,
    connecting: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.medium)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .size(Dimens.huge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(number.toString(), style = MaterialTheme.typography.titleMedium)
            }
            // A short rule rather than one stretched to the next circle: the text beside it can
            // be any length, and a line built to match it would need a custom layout pass.
            if (connecting) {
                Box(
                    modifier =
                        Modifier
                            .padding(vertical = Dimens.extraSmall)
                            .size(width = DividerDefaults.Thickness, height = Dimens.large)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Dimens.small))
    }
}

@Composable
private fun Actions(
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
    onCook: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.large),
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            OutlinedButton(
                onClick = viewModel::save,
                enabled = !state.isWorking && !state.isSaved,
                modifier = Modifier.weight(1f),
            ) {
                val label = if (state.isSaved) Res.string.detail_saved else Res.string.detail_save
                Text(stringResource(label))
            }
            OutlinedButton(
                onClick = viewModel::addMissingToList,
                enabled = !state.isWorking && state.missing.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(Res.string.detail_add_missing)) }
            Button(onClick = onCook, enabled = state.canCook, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.detail_cook))
            }
        }
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
