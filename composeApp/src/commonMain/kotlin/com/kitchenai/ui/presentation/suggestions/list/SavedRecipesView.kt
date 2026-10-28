package com.kitchenai.ui.presentation.suggestions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.ui.designsystem.component.CoverageBar
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.RecipeImage
import com.kitchenai.ui.designsystem.component.tonalCardColors
import com.kitchenai.ui.designsystem.motion.sharedRecipeImage
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_unsave
import com.kitchenai.ui.resources.saved_household_avoids
import com.kitchenai.ui.resources.suggestions_coverage_count
import com.kitchenai.ui.resources.suggestions_minutes
import com.kitchenai.ui.resources.suggestions_saved_empty_body
import com.kitchenai.ui.resources.suggestions_saved_empty_title
import com.kitchenai.ui.resources.suggestions_saved_no_matches
import com.kitchenai.ui.resources.suggestions_saved_search
import com.kitchenai.ui.resources.suggestions_see_ideas
import org.jetbrains.compose.resources.stringResource

// Two columns on a phone from about 400dp wide, one below; more on a tablet.
private val SAVED_CARD_MIN_WIDTH = 180.dp

/**
 * The kept recipes as their own view: compact cards, what can be cooked now first, a search
 * once there are more than a handful, and an empty state that says how a recipe gets here.
 */
@Composable
internal fun SavedRecipesView(
    state: SuggestionsUiState,
    onOpen: (RecipeId) -> Unit,
    onUnsave: (RecipeId) -> Unit,
    onSearch: (String) -> Unit,
    onSeeIdeas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.savedRecipes.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.suggestions_saved_empty_title),
            body = stringResource(Res.string.suggestions_saved_empty_body),
            modifier = modifier.fillMaxSize(),
            icon = Icons.Outlined.FavoriteBorder,
            actionLabel = stringResource(Res.string.suggestions_see_ideas),
            onAction = onSeeIdeas,
        )
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.medium)) {
        if (state.offersSavedSearch) {
            OutlinedTextField(
                value = state.savedQuery,
                onValueChange = onSearch,
                placeholder = { Text(stringResource(Res.string.suggestions_saved_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.visibleSaved.isEmpty()) {
            Text(
                stringResource(Res.string.suggestions_saved_no_matches, state.savedQuery.trim()),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(SAVED_CARD_MIN_WIDTH),
            horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
            contentPadding = PaddingValues(bottom = Dimens.large),
        ) {
            items(state.visibleSaved, key = { recipe -> recipe.id.value }) { recipe ->
                SavedRecipeCard(recipe, onOpen, onUnsave, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun SavedRecipeCard(
    recipe: SuggestionUi,
    onOpen: (RecipeId) -> Unit,
    onUnsave: (RecipeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onOpen(recipe.id) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = tonalCardColors(),
    ) {
        Column {
            RecipeImage(tags = recipe.tagRefs, modifier = Modifier.sharedRecipeImage(recipe.id.value))
            Column(
                modifier = Modifier.padding(Dimens.medium),
                verticalArrangement = Arrangement.spacedBy(Dimens.extraSmall),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        recipe.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The filled heart is the detail screen's own: tapping it lets the recipe go.
                    IconButton(onClick = { onUnsave(recipe.id) }) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(Res.string.detail_unsave),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                recipe.totalMinutes?.let { minutes ->
                    Text(
                        stringResource(Res.string.suggestions_minutes, minutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (recipe.conflictsWithHousehold) {
                    Text(
                        stringResource(Res.string.saved_household_avoids),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                CoverageBar(fraction = recipe.coverage) {
                    Text(
                        stringResource(Res.string.suggestions_coverage_count, recipe.heldCount, recipe.totalCount),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
