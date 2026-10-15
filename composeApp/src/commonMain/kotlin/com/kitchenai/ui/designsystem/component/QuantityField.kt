package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.kitchenai.ui.designsystem.format.parseAmount
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * An amount plus a unit selector. [units] is a list of `id to label`: the component never
 * learns what a unit taxonomy is, and the id it returns is the caller's.
 *
 * The raw text stays local because `"1,"` is a legal keystroke with no `Double` behind it;
 * only parsed values leave, and `null` means "not a number yet". [options] carries what a screen
 * may add on top; the chevron is what shows the unit selector opens a list.
 */
@Composable
fun QuantityField(
    amountLabel: String,
    units: List<Pair<String, String>>,
    onChange: (Double?, String?) -> Unit,
    modifier: Modifier = Modifier,
    initialAmount: String = "",
    initialUnitId: String? = units.firstOrNull()?.first,
    options: QuantityFieldOptions = QuantityFieldOptions(),
) {
    val preferredUnitId = options.preferredUnitId
    var text by rememberSaveable { mutableStateOf(initialAmount) }
    var chosenUnitId by rememberSaveable { mutableStateOf(initialUnitId) }
    // Units can arrive after the field is first composed; the first one is shown, so it is reported.
    val unitId = chosenUnitId ?: units.firstOrNull()?.first
    var malformed by rememberSaveable { mutableStateOf(false) }

    // Keyed on whether the unit exists yet: a preference that arrives before its vocabulary waits.
    val preferredKnown = units.any { (id, _) -> id == preferredUnitId }
    LaunchedEffect(preferredUnitId, preferredKnown) {
        preferredUnitId?.takeIf { preferredKnown }?.let { preferred ->
            chosenUnitId = preferred
            onChange(parseAmount(text), preferred)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = text,
            // Parsing happens in the event, not in composition: the composable stays declarative.
            onValueChange = { input ->
                text = input
                val amount = parseAmount(input)
                malformed = input.isNotBlank() && amount == null
                options.onMalformedChange(malformed)
                onChange(amount, unitId)
            },
            label = { Text(amountLabel) },
            placeholder = options.amountPlaceholder?.let { hint -> { Text(hint) } },
            supportingText = options.malformedMessage?.takeIf { malformed }?.let { message -> { Text(message) } },
            singleLine = true,
            isError = malformed,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )

        if (units.isNotEmpty()) {
            UnitSelector(
                units = units,
                selectedId = unitId,
                unitLabel = options.unitLabel,
                onSelect = { id ->
                    chosenUnitId = id
                    onChange(parseAmount(text), id)
                },
            )
        }
    }
}

@Composable
private fun UnitSelector(
    units: List<Pair<String, String>>,
    selectedId: String?,
    unitLabel: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = units.firstOrNull { (id, _) -> id == selectedId }?.second.orEmpty()
    // Aligned with the field's box, not with the field plus a message under it.
    Box(modifier = Modifier.padding(top = Dimens.small)) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { if (unitLabel != null) contentDescription = "$unitLabel: $shown" },
        ) {
            Text(shown)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}
