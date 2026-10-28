package com.kitchenai.ui.presentation.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kitchenai.shared.domain.model.MoveToPantryOptions
import com.kitchenai.shared.domain.model.expiryDate
import com.kitchenai.ui.designsystem.component.QuantityField
import com.kitchenai.ui.designsystem.component.QuantityFieldOptions
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformFormatDate
import com.kitchenai.ui.presentation.common.ExpiryField
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_editor_amount_malformed
import com.kitchenai.ui.resources.pantry_no_expiry
import com.kitchenai.ui.resources.shopping_amount
import com.kitchenai.ui.resources.shopping_move_bare_amount
import com.kitchenai.ui.resources.shopping_move_count
import com.kitchenai.ui.resources.shopping_move_review_hint
import com.kitchenai.ui.resources.shopping_move_same_date
import com.kitchenai.ui.resources.shopping_move_skip_dates
import com.kitchenai.ui.resources.shopping_move_to_pantry
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a move goes through before it writes: a date per line or one for all, and an amount for a
 * line that has none. Lines are read from the live state, so one unticked meanwhile drops out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToPantrySheet(
    lines: List<ShoppingItemUi>,
    onMove: (MoveToPantryOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val form = remember { MoveReviewForm() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.large)
                    .padding(bottom = Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            Text(
                text = stringResource(Res.string.shopping_move_to_pantry),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.shopping_move_review_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(Res.string.shopping_move_same_date), style = MaterialTheme.typography.titleSmall)
            ExpiryField(value = form.sharedDate, onChange = { date -> form.setAll(date, lines) })
            HorizontalDivider()
            lines.forEach { line -> key(line.id.value) { MoveLineRow(line, form) } }
            MoveButtons(
                count = lines.size,
                enabled = form.canMove(lines),
                onSkipDates = { onMove(form.options(lines, withDates = false)) },
                onMove = { onMove(form.options(lines, withDates = true)) },
            )
        }
    }
}

@Composable
private fun MoveLineRow(
    line: ShoppingItemUi,
    form: MoveReviewForm,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val date = form.dateOf(line.id)
    val open = form.openLine == line.id
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.label, style = MaterialTheme.typography.bodyLarge)
                line.quantity?.let { amount ->
                    Text(
                        amount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { form.openLine = if (open) null else line.id }) {
                Text(
                    date?.let { chosen -> platformFormatDate(chosen.expiryDate(zone)) }
                        ?: stringResource(Res.string.pantry_no_expiry),
                )
            }
        }
        // A line with no amount moves as one; the field is there to say otherwise, never required.
        if (line.amount == null) BareAmountField(line, form)
        if (open) ExpiryField(value = date, onChange = { chosen -> form.setDate(line.id, chosen) })
    }
}

@Composable
private fun BareAmountField(
    line: ShoppingItemUi,
    form: MoveReviewForm,
) {
    QuantityField(
        amountLabel = stringResource(Res.string.shopping_amount),
        units = emptyList(),
        onChange = { amount, _ -> form.setAmount(line.id, amount) },
        options =
            QuantityFieldOptions(
                amountPlaceholder = stringResource(Res.string.shopping_move_bare_amount),
                malformedMessage = stringResource(Res.string.pantry_editor_amount_malformed),
                onMalformedChange = { notANumber -> form.setMalformed(line.id, notANumber) },
            ),
    )
}

@Composable
private fun MoveButtons(
    count: Int,
    enabled: Boolean,
    onSkipDates: () -> Unit,
    onMove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.small, Alignment.End),
    ) {
        OutlinedButton(onClick = onSkipDates, enabled = enabled) {
            Text(stringResource(Res.string.shopping_move_skip_dates))
        }
        Button(onClick = onMove, enabled = enabled) {
            Text(pluralStringResource(Res.plurals.shopping_move_count, count, count))
        }
    }
}
