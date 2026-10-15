package com.kitchenai.ui.presentation.suggestions.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_recipe_unavailable_body
import com.kitchenai.ui.resources.error_recipe_unavailable_title
import com.kitchenai.ui.resources.nav_back
import org.jetbrains.compose.resources.stringResource

/** A route whose recipe id cannot be a recipe: there is nothing to load, only a way out. */
@Composable
fun RecipeUnavailableScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = stringResource(Res.string.error_recipe_unavailable_title),
        body = stringResource(Res.string.error_recipe_unavailable_body),
        modifier = modifier.fillMaxSize(),
        actionLabel = stringResource(Res.string.nav_back),
        onAction = onBack,
    )
}
