package com.kitchenai.ui.presentation.suggestions.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_cancel
import com.kitchenai.ui.resources.detail_cook_anyway
import com.kitchenai.ui.resources.detail_cook_confirm
import com.kitchenai.ui.resources.detail_cook_deduction_left
import com.kitchenai.ui.resources.detail_cook_deduction_used_up
import com.kitchenai.ui.resources.detail_cook_missing_untouched
import com.kitchenai.ui.resources.detail_cook_nothing_held
import com.kitchenai.ui.resources.detail_cook_title
import org.jetbrains.compose.resources.stringResource

/**
 * The cook confirmation, listing every holding it changes and by how much, so "this cannot be
 * undone" is replaced by something the reader can check. Two buttons: the action subtracts food.
 */
@Composable
internal fun CookReviewDialog(
    review: CookReviewUi,
    anyway: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.detail_cook_title)) },
        text = { ReviewBody(review) },
        confirmButton = {
            val haptics = LocalHapticFeedback.current
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onConfirm()
                },
            ) {
                Text(stringResource(if (anyway) Res.string.detail_cook_anyway else Res.string.detail_cook_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.detail_cancel)) } },
    )
}

@Composable
private fun ReviewBody(review: CookReviewUi) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        if (review.deductions.isEmpty()) {
            Text(stringResource(Res.string.detail_cook_nothing_held))
        }
        review.deductions.forEach { line -> DeductionRow(line) }
        if (review.missing.isNotEmpty()) {
            Text(
                stringResource(Res.string.detail_cook_missing_untouched, review.missing.joinToString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeductionRow(line: DeductionUi) {
    val after =
        line.left?.let { stringResource(Res.string.detail_cook_deduction_left, it) }
            ?: stringResource(Res.string.detail_cook_deduction_used_up)
    val spoken = "${line.name}: −${line.taken}, $after"
    // One node: the name, the amount and what is left would otherwise be read apart.
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        Text(line.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column {
            Text("−${line.taken}", style = MaterialTheme.typography.bodyMedium)
            Text(after, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
