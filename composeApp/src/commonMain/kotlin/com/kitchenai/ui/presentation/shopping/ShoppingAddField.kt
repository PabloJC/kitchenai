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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.designsystem.component.QuantityField
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.shopping_add
import com.kitchenai.ui.resources.shopping_add_field
import com.kitchenai.ui.resources.shopping_amount
import org.jetbrains.compose.resources.stringResource

/**
 * The inline add line, pinned under the list and above the keyboard.
 *
 * A picked catalogue entry becomes a chip and free text stays a plain field. That is not styling:
 * a catalogue line merges when the same thing is added twice and a free-text line does not, and
 * the person adding it is the only one who can tell which they meant.
 *
 * The amount is optional, but a line without one can never move to the pantry.
 */
@Composable
fun ShoppingAddField(
    draft: ShoppingDraftUi,
    onDraftChange: (String) -> Unit,
    onPick: (IngredientSuggestion) -> Unit,
    onAdd: () -> Unit,
    units: List<Pair<TermRef, String>>,
    onQuantityChange: (Double?, TermRef?) -> Unit,
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

        if (picked == null) {
            OutlinedTextField(
                value = draft.text,
                onValueChange = onDraftChange,
                label = { Text(stringResource(Res.string.shopping_add_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Tapping the chip goes back to free text, which is the only way out of a wrong pick.
            TermChip(
                label = picked.label,
                selected = true,
                onToggle = { onDraftChange("") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Keyed: the field keeps its raw text locally and must start empty after an add.
            key(draft.revision) {
                QuantityField(
                    amountLabel = stringResource(Res.string.shopping_amount),
                    units = units.map { (ref, label) -> ref.term.value to label },
                    onChange = { amount, unitId ->
                        onQuantityChange(amount, units.firstOrNull { (ref, _) -> ref.term.value == unitId }?.first)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = onAdd,
                enabled = picked != null || draft.text.isNotBlank(),
            ) {
                Text(stringResource(Res.string.shopping_add))
            }
        }
    }
}
