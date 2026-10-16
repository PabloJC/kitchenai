package com.kitchenai.ui.presentation.suggestions.cooking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.presentation.suggestions.detail.IngredientLineUi
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.cooking_ingredients
import com.kitchenai.ui.resources.detail_optional_suffix
import org.jetbrains.compose.resources.stringResource

/** Every line, already scaled to the servings chosen on recipe detail, ticked off as it is measured out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IngredientsSheet(
    lines: List<IngredientLineUi>,
    ticked: Set<Int>,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Dimens.extraLarge),
        ) {
            Text(
                stringResource(Res.string.cooking_ingredients),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Dimens.large, vertical = Dimens.small).semantics { heading() },
            )
            lines.forEachIndexed { index, line -> TickRow(line, index in ticked) { onToggle(index) } }
        }
    }
}

@Composable
private fun TickRow(
    line: IngredientLineUi,
    ticked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // The whole row is the target, and reads as one checkbox rather than a box beside two texts.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.cookingControl)
                .toggleable(value = ticked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = Dimens.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
    ) {
        Checkbox(checked = ticked, onCheckedChange = null)
        val decoration = if (ticked) TextDecoration.LineThrough else null
        val tint = if (ticked) colors.onSurfaceVariant else colors.onSurface
        Text(
            line.name + if (line.optional) stringResource(Res.string.detail_optional_suffix) else "",
            style = MaterialTheme.typography.titleMedium,
            textDecoration = decoration,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        line.quantity?.let { amount ->
            Text(amount, style = MaterialTheme.typography.titleMedium, textDecoration = decoration, color = tint)
        }
    }
}
