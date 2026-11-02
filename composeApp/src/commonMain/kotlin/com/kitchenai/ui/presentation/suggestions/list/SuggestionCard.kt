package com.kitchenai.ui.presentation.suggestions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.ui.designsystem.component.CoverageBar
import com.kitchenai.ui.designsystem.component.RecipeImage
import com.kitchenai.ui.designsystem.component.Tag
import com.kitchenai.ui.designsystem.component.tonalCardColors
import com.kitchenai.ui.designsystem.motion.sharedRecipeImage
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.presentation.common.SaveRecipeToggle
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.suggestions_add_missing
import com.kitchenai.ui.resources.suggestions_check_manually
import com.kitchenai.ui.resources.suggestions_coverage_count
import com.kitchenai.ui.resources.suggestions_generated
import com.kitchenai.ui.resources.suggestions_minutes
import com.kitchenai.ui.resources.suggestions_missing
import com.kitchenai.ui.resources.suggestions_missing_more
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SuggestionCard(
    suggestion: SuggestionUi,
    saved: Boolean,
    actions: SuggestionCardActions,
    addingMissing: Boolean,
    modifier: Modifier = Modifier,
) {
    // The whole card opens the recipe: a dedicated "Open" button repeated what tapping the card
    // already does everywhere else in the app.
    Card(
        onClick = { actions.onOpen(suggestion.id) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = tonalCardColors(),
    ) {
        Column {
            RecipeImage(
                tags = suggestion.tagRefs,
                imageUrl = suggestion.imageUrl,
                modifier = Modifier.sharedRecipeImage(suggestion.id.value),
            )
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
                    SaveRecipeToggle(saved = saved, onToggle = { actions.onToggleSaved(suggestion.id) })
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
                        label = stringResource(Res.string.suggestions_minutes, minutes),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SuggestionCoverage(suggestion, actions.onAddMissing, addingMissing)
            }
        }
    }
}

@Composable
private fun SuggestionCoverage(
    suggestion: SuggestionUi,
    onAddMissing: (RecipeId) -> Unit,
    addingMissing: Boolean,
) {
    CoverageBar(fraction = suggestion.coverage) {
        Text(
            stringResource(Res.string.suggestions_coverage_count, suggestion.heldCount, suggestion.totalCount),
            style = MaterialTheme.typography.labelSmall,
        )
    }
    if (suggestion.missing.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                missingSentence(suggestion),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAddMissing(suggestion.id) }, enabled = !addingMissing) {
                Icon(
                    Icons.Outlined.AddShoppingCart,
                    contentDescription = stringResource(Res.string.suggestions_add_missing),
                )
            }
        }
    }
    // Its own line, its own wording. Folding these into "missing" would claim the pantry knows
    // something it does not.
    if (suggestion.unverifiable.isNotEmpty()) {
        Text(
            stringResource(Res.string.suggestions_check_manually, suggestion.unverifiable.joinToString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** At most three names, then a count: a long comma-joined line buried the card's other facts. */
@Composable
private fun missingSentence(suggestion: SuggestionUi): String {
    val shown = stringResource(Res.string.suggestions_missing, suggestion.shownMissing.joinToString())
    val hidden = suggestion.hiddenMissingCount
    if (hidden == 0) return shown
    return "$shown ${pluralStringResource(Res.plurals.suggestions_missing_more, hidden, hidden)}"
}
