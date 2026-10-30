package com.kitchenai.ui.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitchenai.shared.domain.model.expiryDate
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.PlatformDatePicker
import com.kitchenai.ui.platform.platformFormatDate
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_clear
import com.kitchenai.ui.resources.pantry_expiry_in_1_month
import com.kitchenai.ui.resources.pantry_expiry_in_1_week
import com.kitchenai.ui.resources.pantry_expiry_in_3_days
import com.kitchenai.ui.resources.pantry_expiry_pick
import com.kitchenai.ui.resources.pantry_no_expiry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/** A date with its shortcuts, a picker and Clear: the pantry editor's and the move-to-pantry sheet's. */
@Composable
fun ExpiryField(
    value: Instant?,
    onChange: (Instant?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val zone = remember { TimeZone.currentSystemDefault() }

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

    // Opened on the date on the row each time, so a Clear is never undone by the next Done.
    if (picking) {
        PlatformDatePicker(
            initial = value?.expiryDate(zone),
            onPick = { day ->
                onChange(expiryFromPicker(day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), zone))
                picking = false
            },
            onDismiss = { picking = false },
        )
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
