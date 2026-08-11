package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kitchenai.ui.designsystem.format.parseAmount
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * An amount plus a unit selector. [units] is a list of `id to label`: the component never
 * learns what a unit taxonomy is, and the id it returns is the caller's.
 *
 * The raw text stays local because `"1,"` is a legal keystroke with no `Double` behind it;
 * only parsed values leave, and `null` means "not a number yet".
 */
@Composable
fun QuantityField(
    amountLabel: String,
    units: List<Pair<String, String>>,
    onChange: (Double?, String?) -> Unit,
    modifier: Modifier = Modifier,
    initialAmount: String = "",
    initialUnitId: String? = units.firstOrNull()?.first,
) {
    var text by rememberSaveable { mutableStateOf(initialAmount) }
    var unitId by rememberSaveable { mutableStateOf(initialUnitId) }
    var malformed by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            // Parsing happens in the event, not in composition: the composable stays declarative.
            onValueChange = { input ->
                text = input
                val amount = parseAmount(input)
                malformed = input.isNotBlank() && amount == null
                onChange(amount, unitId)
            },
            label = { Text(amountLabel) },
            singleLine = true,
            isError = malformed,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )

        if (units.isNotEmpty()) {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(units.firstOrNull { (id, _) -> id == unitId }?.second.orEmpty())
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    units.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                unitId = id
                                expanded = false
                                onChange(parseAmount(text), id)
                            },
                        )
                    }
                }
            }
        }
    }
}
