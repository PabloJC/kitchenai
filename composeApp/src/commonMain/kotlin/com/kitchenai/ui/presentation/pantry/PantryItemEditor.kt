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
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_amount
import com.kitchenai.ui.resources.pantry_clear
import com.kitchenai.ui.resources.pantry_done
import com.kitchenai.ui.resources.pantry_expiry
import com.kitchenai.ui.resources.pantry_no_expiry
import com.kitchenai.ui.resources.pantry_save
import com.kitchenai.ui.resources.pantry_search
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * The add and edit sheet. It holds the draft locally and reports it once, so a half-typed amount
 * never reaches a use case.
 *
 * The unit selector offers the vocabulary the catalogue measures ingredients in and the chips the
 * one the pantry already stores them in; neither list is written down here. Typing a line the
 * search does not find is not blocked: it becomes free text, the way the shopping field's does.
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
    var query by rememberSaveable(editing) { mutableStateOf(editing?.freeText.orEmpty()) }
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
                query = query,
                // Typing again after a pick is changing your mind, not refining it: the pick
                // goes with the text that no longer names it.
                onQueryChange = { input ->
                    query = input
                    ingredient = null
                },
                selected = ingredient,
                onSelect = { chosen -> ingredient = chosen },
            )
            QuantityField(
                amountLabel = stringResource(Res.string.pantry_amount),
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

            val draft = draftOf(ingredient, query, amount, unit, location, expiresAt)
            Button(
                onClick = { draft?.let(onSubmit) },
                enabled = draft != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.pantry_save)) }
        }
    }
}

/** Nothing is savable without a source — a pick or typed text — and an amount. */
private fun draftOf(
    ingredient: IngredientId?,
    query: String,
    amount: Double?,
    unit: TermRef?,
    location: TermRef?,
    expiresAt: Instant?,
): PantryItemDraft? {
    if (amount == null) return null
    val text = query.trim().takeIf { it.isNotEmpty() }
    return when {
        ingredient != null -> PantryItemDraft(ingredient, null, amount, unit, location, expiresAt)
        text != null -> PantryItemDraft(null, text, amount, unit, location, expiresAt)
        else -> null
    }
}

/** Searchable rather than scrollable: a real catalogue is thousands of entries long. */
@Composable
private fun IngredientPicker(
    options: List<Pair<IngredientId, String>>,
    query: String,
    onQueryChange: (String) -> Unit,
    selected: IngredientId?,
    onSelect: (IngredientId) -> Unit,
) {
    val matches =
        remember(options, query) {
            options.filter { (_, label) -> label.contains(query, ignoreCase = true) }
        }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(Res.string.pantry_search)) },
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
            text = value?.toString()?.substringBefore('T') ?: stringResource(Res.string.pantry_no_expiry),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { picking = true }) { Text(stringResource(Res.string.pantry_expiry)) }
        if (value != null) TextButton(onClick = { onChange(null) }) { Text(stringResource(Res.string.pantry_clear)) }
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
                ) { Text(stringResource(Res.string.pantry_done)) }
            },
        ) { DatePicker(state = picker) }
    }
}
