package com.kitchenai.ui.presentation.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.designsystem.component.QuantityField
import com.kitchenai.ui.designsystem.component.QuantityFieldOptions
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.format.formatAmount
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.shopping_add
import com.kitchenai.ui.resources.shopping_add_field
import com.kitchenai.ui.resources.shopping_amount
import com.kitchenai.ui.resources.shopping_done
import com.kitchenai.ui.resources.shopping_on_list
import com.kitchenai.ui.resources.shopping_quantity_toggle
import com.kitchenai.ui.resources.shopping_unpick
import org.jetbrains.compose.resources.stringResource

/**
 * The add bar, pinned under the list and above the keyboard, and one row at rest so the list
 * keeps its height: "2 kg potatoes" in one field and Enter adds it. The amount row opens only on
 * demand.
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
    units: List<Pair<TermRef, String>>,
    onQuantityChange: (Double?, TermRef?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var quantityOpen by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // After an add the field is back and empty: focus returns to it, so the keyboard stays up.
    LaunchedEffect(draft.revision) { if (draft.revision > 0) focus.requestFocus() }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.large, vertical = Dimens.small),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        // Above the field, never below it: below is where the keyboard is.
        if (draft.suggestions.isNotEmpty()) Suggestions(draft.suggestions, onPick)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val picked = draft.picked
            if (picked == null) {
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = onDraftChange,
                    placeholder = { Text(stringResource(Res.string.shopping_add_field)) },
                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    // Handled here, so the keyboard does not close: the next item follows.
                    keyboardActions = KeyboardActions(onSend = { onAdd() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            } else {
                PickedChip(label = picked.label, onUnpick = { onDraftChange("") }, modifier = Modifier.weight(1f))
            }
            IconToggleButton(checked = quantityOpen, onCheckedChange = { open -> quantityOpen = open }) {
                Icon(Icons.Outlined.Scale, contentDescription = stringResource(Res.string.shopping_quantity_toggle))
            }
            FilledIconButton(onClick = onAdd, enabled = draft.picked != null || draft.text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(Res.string.shopping_add))
            }
        }

        if (quantityOpen) {
            // Keyed: the field keeps its raw text locally and must start again after an add, or
            // from an amount a pick carried over from the typed line.
            key(draft.revision, draft.picked) {
                QuantityField(
                    amountLabel = stringResource(Res.string.shopping_amount),
                    units = units.map { (ref, label) -> ref.term.value to label },
                    onChange = { amount, unitId ->
                        onQuantityChange(amount, units.firstOrNull { (ref, _) -> ref.term.value == unitId }?.first)
                    },
                    initialAmount = draft.amount?.let(::formatAmount).orEmpty(),
                    initialUnitId = draft.unit?.term?.value ?: units.firstOrNull()?.first?.term?.value,
                    modifier = Modifier.fillMaxWidth(),
                    // Done adds the line, as Send does in the name field.
                    options =
                        QuantityFieldOptions(
                            imeAction = ImeAction.Done,
                            onImeAction = onAdd,
                            doneLabel = stringResource(Res.string.shopping_done),
                        ),
                )
            }
        }
    }
}

@Composable
private fun Suggestions(
    suggestions: List<IngredientSuggestion>,
    onPick: (IngredientSuggestion) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
        items(suggestions, key = { suggestion -> suggestion.id.value }) { suggestion ->
            // Says so before a tap: picking one already on the list adds to that line.
            val label =
                if (suggestion.onList) {
                    stringResource(Res.string.shopping_on_list, suggestion.label)
                } else {
                    suggestion.label
                }
            TermChip(label = label, selected = false, onToggle = { onPick(suggestion) })
        }
    }
}

/** The pick, with a visible way out: the × goes back to an empty field. */
@Composable
private fun PickedChip(
    label: String,
    onUnpick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = true,
        onClick = onUnpick,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.shopping_unpick),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        modifier = modifier.heightIn(min = Dimens.touchTarget),
    )
}
