package com.kitchenai.ui.presentation.pantry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.designsystem.component.QuantityField
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.theme.Dimens
import kotlin.time.Instant

// Wording only. Every option the sheet offers comes from the catalogue.
private const val SEARCH_LABEL = "Search the catalogue"
private const val AMOUNT_LABEL = "Amount"
private const val SAVE_LABEL = "Save"
private const val CONFIRM_LABEL = "Done"
private const val CLEAR_LABEL = "Clear"
private const val EXPIRY_LABEL = "Expiry date"
private const val NO_EXPIRY_LABEL = "No expiry date"

/**
 * The add and edit sheet. It holds the draft locally and reports it once, so a half-typed amount
 * never reaches a use case.
 *
 * The unit selector offers the vocabulary the catalogue measures ingredients in and the chips the
 * one the pantry already stores them in; neither list is written down here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryItemEditor(
    state: PantryUiState,
    onDismiss: () -> Unit,
    onSubmit: (PantryItemDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = state.editing
    var ingredient by remember(editing) { mutableStateOf(editing?.ingredient) }
    var amount by remember(editing) { mutableStateOf(editing?.amount) }
    var unit by remember(editing) { mutableStateOf(editing?.unit) }
    var location by remember(editing) { mutableStateOf(editing?.location) }
    var expiresAt by remember(editing) { mutableStateOf(editing?.expiresAt) }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = Dimens.large).padding(bottom = Dimens.extraLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            IngredientPicker(
                options = state.ingredients,
                selected = ingredient,
                onSelect = { chosen -> ingredient = chosen },
            )
            QuantityField(
                amountLabel = AMOUNT_LABEL,
                units = state.units.map { (ref, label) -> ref.term.value to label },
                onChange = { typed, unitId ->
                    amount = typed
                    unit = state.units.firstOrNull { (ref, _) -> ref.term.value == unitId }?.first
                },
                initialAmount = editing?.amount?.toString().orEmpty(),
                initialUnitId = (editing?.unit ?: state.units.firstOrNull()?.first)?.term?.value,
            )
            LocationChips(
                options = state.locations,
                selected = location,
                onToggle = { ref -> location = if (location == ref) null else ref },
            )
            ExpiryField(value = expiresAt, onChange = { picked -> expiresAt = picked })

            val draft = draftOf(ingredient, amount, unit, location, expiresAt)
            Button(
                onClick = { draft?.let(onSubmit) },
                enabled = draft != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(SAVE_LABEL) }
        }
    }
}

/** Nothing is savable without an ingredient and an amount, which is what disables the button. */
private fun draftOf(
    ingredient: IngredientId?,
    amount: Double?,
    unit: TermRef?,
    location: TermRef?,
    expiresAt: Instant?,
): PantryItemDraft? =
    if (ingredient == null || amount == null) {
        null
    } else {
        PantryItemDraft(ingredient, amount, unit, location, expiresAt)
    }

/** Searchable rather than scrollable: a real catalogue is thousands of entries long. */
@Composable
private fun IngredientPicker(
    options: List<Pair<IngredientId, String>>,
    selected: IngredientId?,
    onSelect: (IngredientId) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches =
        remember(options, query) {
            options.filter { (_, label) -> label.contains(query, ignoreCase = true) }
        }

    OutlinedTextField(
        value = query,
        onValueChange = { input -> query = input },
        label = { Text(SEARCH_LABEL) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn(modifier = Modifier.heightIn(max = Dimens.touchTarget * 4)) {
        items(matches, key = { (id, _) -> id.value }) { (id, label) ->
            val background =
                if (id == selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            ListItem(
                headlineContent = { Text(label) },
                colors = ListItemDefaults.colors(containerColor = background),
                modifier = Modifier.clickable { onSelect(id) },
            )
        }
    }
}

/** Absent rather than empty: a pantry whose vocabulary has no storage places offers none. */
@Composable
private fun LocationChips(
    options: List<Pair<TermRef, String>>,
    selected: TermRef?,
    onToggle: (TermRef) -> Unit,
) {
    if (options.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
        items(options, key = { (ref, _) -> ref.term.value }) { (ref, label) ->
            TermChip(label = label, selected = ref == selected, onToggle = { onToggle(ref) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryField(
    value: Instant?,
    onChange: (Instant?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // Seeded from the date on the row, and rebuilt whenever that date changes: seeded once, it
    // kept the old selection after Clear and put it back the next time Done was tapped.
    val picker = key(value) { rememberDatePickerState(initialSelectedDateMillis = value?.toEpochMilliseconds()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // The ISO date, in UTC: formatting one per locale needs a date library this module does
        // not depend on, and an approximate format is worse than an unambiguous one.
        Text(
            text = value?.toString()?.substringBefore('T') ?: NO_EXPIRY_LABEL,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { picking = true }) { Text(EXPIRY_LABEL) }
        if (value != null) TextButton(onClick = { onChange(null) }) { Text(CLEAR_LABEL) }
    }

    if (picking) {
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(picker.selectedDateMillis?.let { millis -> Instant.fromEpochMilliseconds(millis) })
                        picking = false
                    },
                ) { Text(CONFIRM_LABEL) }
            },
        ) { DatePicker(state = picker) }
    }
}
