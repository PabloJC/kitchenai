package com.kitchenai.ui.presentation.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * The inline add line, pinned under the list and above the keyboard.
 *
 * A picked catalogue entry becomes a chip and free text stays a plain field. That is not styling:
 * a catalogue line merges when the same thing is added twice and a free-text line does not, and
 * the person adding it is the only one who can tell which they meant.
 */
@Composable
fun ShoppingAddField(
    draft: ShoppingDraftUi,
    onDraftChange: (String) -> Unit,
    onPick: (IngredientSuggestion) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = draft.picked

    Column(
        modifier = modifier.fillMaxWidth().padding(Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        // Above the field, never below it: below is where the keyboard is.
        if (draft.suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
                items(draft.suggestions, key = { suggestion -> suggestion.id.value }) { suggestion ->
                    TermChip(
                        label = suggestion.label,
                        selected = false,
                        onToggle = { onPick(suggestion) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (picked == null) {
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = onDraftChange,
                    label = { Text(FIELD_LABEL) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Tapping the chip goes back to free text, which is the only way out of a wrong pick.
                TermChip(
                    label = picked.label,
                    selected = true,
                    onToggle = { onDraftChange("") },
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = onAdd,
                enabled = picked != null || draft.text.isNotBlank(),
            ) {
                Text(ADD_LABEL)
            }
        }
    }
}

private const val FIELD_LABEL = "Add an item"
private const val ADD_LABEL = "Add"
