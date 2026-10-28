package com.kitchenai.ui.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.kitchenai.shared.domain.model.expiryDate
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformFormatDate
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_clear
import com.kitchenai.ui.resources.pantry_done
import com.kitchenai.ui.resources.pantry_expiry_in_1_month
import com.kitchenai.ui.resources.pantry_expiry_in_1_week
import com.kitchenai.ui.resources.pantry_expiry_in_3_days
import com.kitchenai.ui.resources.pantry_expiry_pick
import com.kitchenai.ui.resources.pantry_no_expiry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/** A date with its shortcuts, a picker and Clear: the pantry editor's and the move-to-pantry sheet's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryField(
    value: Instant?,
    onChange: (Instant?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val zone = remember { TimeZone.currentSystemDefault() }
    // Seeded from the date on the row, and rebuilt whenever that date changes: seeded once, it
    // kept the old selection after Clear and put it back the next time Done was tapped.
    val picker = key(value) { rememberDatePickerState(initialSelectedDateMillis = value?.toPickerMillis(zone)) }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    value?.let { date -> platformFormatDate(date.expiryDate(zone)) }
                        ?: stringResource(Res.string.pantry_no_expiry),
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                TextButton(
                    onClick = { onChange(null) },
                ) { Text(stringResource(Res.string.pantry_clear)) }
            }
        }
        ExpiryShortcuts(onPick = { count, unit -> onChange(expiryIn(count, unit, Clock.System.now(), zone)) }) {
            picking = true
        }
    }

    if (picking) {
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(picker.selectedDateMillis?.let { millis -> expiryFromPicker(millis, zone) })
                        picking = false
                    },
                ) { Text(stringResource(Res.string.pantry_done)) }
            },
        ) { DatePicker(state = picker) }
    }
}

/** Most food keeps for a few days, a week or a month: one tap instead of a calendar. */
@Composable
private fun ExpiryShortcuts(
    onPick: (Int, DateTimeUnit.DateBased) -> Unit,
    onOpenPicker: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
        AssistChip(onClick = {
            onPick(3, DateTimeUnit.DAY)
        }, label = { Text(stringResource(Res.string.pantry_expiry_in_3_days)) })
        AssistChip(onClick = {
            onPick(1, DateTimeUnit.WEEK)
        }, label = { Text(stringResource(Res.string.pantry_expiry_in_1_week)) })
        AssistChip(onClick = {
            onPick(1, DateTimeUnit.MONTH)
        }, label = { Text(stringResource(Res.string.pantry_expiry_in_1_month)) })
        AssistChip(onClick = onOpenPicker, label = { Text(stringResource(Res.string.pantry_expiry_pick)) })
    }
}
