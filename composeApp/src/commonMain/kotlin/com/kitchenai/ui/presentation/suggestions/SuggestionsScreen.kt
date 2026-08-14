package com.kitchenai.ui.presentation.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.CoverageBar
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformLanguageTags
import org.koin.compose.viewmodel.koinViewModel

private const val GENERATE_LABEL = "Suggest something"
private const val REGENERATE_LABEL = "Suggest again"
private const val ONLY_PANTRY_LABEL = "Only what I have"
private const val QUICK_LABEL = "Under 30 minutes"
private const val QUICK_MINUTES = 30
private const val EMPTY_TITLE = "No suggestions yet"
private const val EMPTY_BODY = "Ask for ideas built from what your pantry already holds"
private const val NOTHING_TITLE = "Nothing to suggest"
private const val NOTHING_BODY = "Add a few things to your pantry and ask again"
private const val WORKING_LABEL = "Reading your pantry and thinking of dishes. This takes a moment."
private const val AGENT_LABEL = "Generated"
private const val SKELETON_CARDS = 3

@Composable
fun SuggestionsScreen(
    userId: UserId,
    modifier: Modifier = Modifier,
    onOpen: (RecipeId) -> Unit = {},
    viewModel: SuggestionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(userId) { viewModel.start(userId, platformLanguageTags()) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event is SuggestionsEvent.Failed) snackbar.showSnackbar(event.message) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            Options(state, viewModel)
            Button(
                onClick = viewModel::generate,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.hasGenerated) REGENERATE_LABEL else GENERATE_LABEL)
            }
            state.error?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) }
            Results(state, onOpen)
        }
    }
}

@Composable
private fun Options(
    state: SuggestionsUiState,
    viewModel: SuggestionsViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
        FilterChip(
            selected = state.options.useOnlyPantry,
            onClick = { viewModel.setUseOnlyPantry(!state.options.useOnlyPantry) },
            label = { Text(ONLY_PANTRY_LABEL) },
        )
        FilterChip(
            selected = state.options.maxMinutes != null,
            onClick = { viewModel.setMaxMinutes(if (state.options.maxMinutes == null) QUICK_MINUTES else null) },
            label = { Text(QUICK_LABEL) },
        )
    }
}

@Composable
private fun Results(
    state: SuggestionsUiState,
    onOpen: (RecipeId) -> Unit,
) {
    when {
        // A skeleton rather than a spinner: the call takes the better part of a minute, and a
        // blank screen for that long reads as a hang rather than as work.
        state.isGenerating -> Skeleton()
        state.suggestions.isNotEmpty() ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.medium)) {
                items(state.suggestions, key = { it.id.value }) { suggestion ->
                    SuggestionCard(suggestion, onOpen)
                }
            }
        // Generated and found nothing is not the same as never asked, and neither is an error.
        state.hasGenerated && state.error == null -> EmptyState(title = NOTHING_TITLE, body = NOTHING_BODY)
        !state.hasGenerated -> EmptyState(title = EMPTY_TITLE, body = EMPTY_BODY)
        else -> Unit
    }
}

@Composable
private fun Skeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.medium)) {
        Text(WORKING_LABEL, style = MaterialTheme.typography.bodyMedium)
        repeat(SKELETON_CARDS) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Dimens.large),
                    verticalArrangement = Arrangement.spacedBy(Dimens.small),
                ) {
                    CoverageBar(fraction = 0f)
                    CoverageBar(fraction = 0f)
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: SuggestionUi,
    onOpen: (RecipeId) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(suggestion.title, style = MaterialTheme.typography.titleMedium)
                if (suggestion.provenance != null) {
                    Text(AGENT_LABEL, style = MaterialTheme.typography.labelSmall)
                }
            }
            // Agent and model, not only that something generated this: whoever reports a bad
            // dish has to be able to say which one wrote it.
            suggestion.provenance?.let { by ->
                Text(
                    "${by.agentId} · ${by.modelId}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            suggestion.summary?.let { summary ->
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            suggestion.totalMinutes?.let {
                    minutes ->
                Text("$minutes min", style = MaterialTheme.typography.labelSmall)
            }
            CoverageBar(fraction = suggestion.coverage) {
                Text(
                    "${suggestion.heldCount} of ${suggestion.totalCount} ingredients",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (suggestion.missing.isNotEmpty()) {
                Text("Missing: ${suggestion.missing.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
            // Its own line, its own wording. Folding these into "missing" would claim the pantry
            // knows something it does not.
            if (suggestion.unverifiable.isNotEmpty()) {
                Text(
                    "Check manually: ${suggestion.unverifiable.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { onOpen(suggestion.id) }) { Text("Open") }
        }
    }
}
