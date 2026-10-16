package com.kitchenai.ui.presentation.suggestions.cooking

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.cooking_finish_body
import com.kitchenai.ui.resources.cooking_finish_confirm
import com.kitchenai.ui.resources.cooking_finish_skip
import com.kitchenai.ui.resources.cooking_finish_title
import org.jetbrains.compose.resources.stringResource

/** The last step's way out: the same subtraction as recipe detail's Cook this, or leaving without it. */
@Composable
internal fun FinishDialog(
    onSubtract: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.cooking_finish_title)) },
        text = { Text(stringResource(Res.string.cooking_finish_body)) },
        confirmButton = { Button(onClick = onSubtract) { Text(stringResource(Res.string.cooking_finish_confirm)) } },
        dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(Res.string.cooking_finish_skip)) } },
    )
}
