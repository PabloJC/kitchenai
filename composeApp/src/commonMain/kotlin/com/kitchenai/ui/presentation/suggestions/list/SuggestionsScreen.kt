package com.kitchenai.ui.presentation.suggestions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.kitchenai.ui.designsystem.component.RecipeImagePlaceholder
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.component.SkeletonBar
import com.kitchenai.ui.designsystem.component.Tag
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.presentation.common.text
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.suggestions_empty_body
import com.kitchenai.ui.resources.suggestions_empty_title
import com.kitchenai.ui.resources.suggestions_generated
import com.kitchenai.ui.resources.suggestions_missing
import com.kitchenai.ui.resources.suggestions_nothing_body
import com.kitchenai.ui.resources.suggestions_nothing_title
import com.kitchenai.ui.resources.suggestions_only_pantry
import com.kitchenai.ui.resources.suggestions_quick
import com.kitchenai.ui.resources.suggestions_saved_title
import com.kitchenai.ui.resources.suggestions_working
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val QUICK_MINUTES = 30
private const val SKELETON_CARDS = 3
private const val SKELETON_TITLE_WIDTH = 0.7f
private const val SKELETON_SUMMARY_WIDTH = 0.85f

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
        viewModel.events.collect { event ->
            if (event is SuggestionsEvent.Failed) snackbar.showSnackbar(event.message.text())
        }
    }

    // The insets are AppShell's; a second round here would open a gap between the toolbar and
    // the bottom bar rather than closing one.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            Options(state, viewModel)
            state.error?.let { message -> Text(message.resolve(), color = MaterialTheme.colorScheme.error) }
            Results(state, onOpen, onRefresh = viewModel::generate)
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
            label = { Text(stringResource(Res.string.suggestions_only_pantry)) },
        )
        FilterChip(
            selected = state.options.maxMinutes != null,
            onClick = { viewModel.setMaxMinutes(if (state.options.maxMinutes == null) QUICK_MINUTES else null) },
            label = { Text(stringResource(Res.string.suggestions_quick)) },
        )
    }
}

@Composable
private fun Results(
    state: SuggestionsUiState,
    onOpen: (RecipeId) -> Unit,
    onRefresh: () -> Unit,
) {
    // Pulling down calls the same generate() the button does, guarded the same way: a pull
    // while one is already in flight is a no-op rather than a second run.
    PullToRefreshBox(
        isRefreshing = state.isGenerating,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.medium)) {
            when {
                // Before isGenerating: a stored set from the last launch stays on screen while a
                // new one runs behind it, rather than being hidden by a skeleton for the better
                // part of a minute.
                state.suggestions.isNotEmpty() ->
                    items(state.suggestions, key = { it.id.value }) { suggestion -> SuggestionCard(suggestion, onOpen) }
                // A skeleton rather than a spinner: the call takes the better part of a minute,
                // and a blank screen for that long reads as a hang rather than as work.
                state.isGenerating -> item { Skeleton() }
                // Generated and found nothing is not the same as never asked, and neither is an error.
                state.hasGenerated && state.error == null ->
                    item {
                        EmptyState(
                            title = stringResource(Res.string.suggestions_nothing_title),
                            body = stringResource(Res.string.suggestions_nothing_body),
                        )
                    }
                !state.hasGenerated ->
                    item {
                        EmptyState(
                            title = stringResource(Res.string.suggestions_empty_title),
                            body = stringResource(Res.string.suggestions_empty_body),
                        )
                    }
                else -> Unit
            }
            // Always last: a save is a standing choice the user made on some earlier visit, not
            // this session's answer, so it never competes with what was just generated for the
            // top of the list.
            if (state.savedRecipes.isNotEmpty()) {
                item { SectionHeader(title = stringResource(Res.string.suggestions_saved_title)) }
                items(
                    state.savedRecipes,
                    key = { "saved-${it.id.value}" },
                ) { suggestion -> SuggestionCard(suggestion, onOpen) }
            }
        }
    }
}

@Composable
private fun Skeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.medium)) {
        Text(stringResource(Res.string.suggestions_working), style = MaterialTheme.typography.bodyMedium)
        repeat(SKELETON_CARDS) { SkeletonCard() }
    }
}

/**
 * The same shape as [SuggestionCard] — the placeholder image, a title bar, two summary lines and
 * a coverage bar — so the layout does not jump once real cards replace it.
 */
@Composable
private fun SkeletonCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
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
) {
    // The whole card opens the recipe: a dedicated "Open" button repeated what tapping the card
    // already does everywhere else in the app.
    Card(onClick = { onOpen(suggestion.id) }, modifier = Modifier.fillMaxWidth()) {
        Column {
            // The slot a photograph will occupy once the catalogue has one: even tonal and
            // empty, it is what sets the card's proportions rather than leaving it a text slab.
            RecipeImagePlaceholder()
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
                        label = "$minutes min",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SuggestionCoverage(suggestion)
            }
        }
    }
}

@Composable
private fun SuggestionCoverage(suggestion: SuggestionUi) {
    CoverageBar(fraction = suggestion.coverage) {
        Text(
            "${suggestion.heldCount} of ${suggestion.totalCount} ingredients",
            style = MaterialTheme.typography.labelSmall,
        )
    }
    if (suggestion.missing.isNotEmpty()) {
        Text(
            stringResource(Res.string.suggestions_missing, suggestion.missing.joinToString()),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    // Its own line, its own wording. Folding these into "missing" would claim the pantry knows
    // something it does not.
    if (suggestion.unverifiable.isNotEmpty()) {
        Text(
            "Check manually: ${suggestion.unverifiable.joinToString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
