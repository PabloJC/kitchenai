package com.kitchenai.ui.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kitchenai.shared.domain.usecase.profile.SetDisplayNameUseCase
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.display_name_body
import com.kitchenai.ui.resources.display_name_cancel
import com.kitchenai.ui.resources.display_name_label
import com.kitchenai.ui.resources.display_name_save
import com.kitchenai.ui.resources.display_name_title
import org.jetbrains.compose.resources.stringResource

/** Shown while [DisplayNameUiState.isEditing]; Done saves, as the Save button does. */
@Composable
fun DisplayNameDialog(
    state: DisplayNameUiState,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.isEditing) return
    var text by rememberSaveable { mutableStateOf(state.name.orEmpty()) }
    val valid = text.trim().length in 1..SetDisplayNameUseCase.MAX_LENGTH
    val save = { if (valid && !state.isSaving) onSave(text) }
    val field = remember { FocusRequester() }
    LaunchedEffect(field) { field.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.display_name_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.display_name_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    // Capped as typed: a longer name would only be refused on Save.
                    onValueChange = { typed -> text = typed.take(SetDisplayNameUseCase.MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.display_name_label)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = state.error?.let { message -> { Text(message.resolve()) } },
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.focusRequester(field),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = valid && !state.isSaving) {
                Text(stringResource(Res.string.display_name_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.display_name_cancel)) } },
    )
}
