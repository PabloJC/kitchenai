package com.kitchenai.ui.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.kitchen_join_action
import com.kitchenai.ui.resources.kitchen_join_placeholder
import com.kitchenai.ui.resources.kitchen_paste
import org.jetbrains.compose.resources.stringResource

/** The join code and its Join button, shared by the Kitchen screen and the first run. */
@Composable
fun JoinCodeField(
    value: String,
    isBusy: Boolean,
    onChange: (String) -> Unit,
    onPaste: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester = remember { FocusRequester() },
) {
    val canJoin = !isBusy && value.isNotBlank()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(stringResource(Res.string.kitchen_join_placeholder)) },
            trailingIcon = { if (value.isEmpty()) PasteAction(onPaste) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { if (canJoin) onJoin() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Button(onClick = onJoin, enabled = canJoin) {
            Text(stringResource(Res.string.kitchen_join_action))
        }
    }
}

/**
 * Offered only while the clipboard holds text, checked on every resume. Only asking whether there
 * is text, not reading it, keeps iOS from showing its paste prompt until the user taps.
 */
@Composable
private fun PasteAction(onPaste: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var hasText by remember { mutableStateOf(false) }
    LifecycleResumeEffect(clipboard) {
        hasText = clipboard.hasText()
        onPauseOrDispose {}
    }
    if (hasText) {
        IconButton(onClick = { clipboard.getText()?.text?.let(onPaste) }) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(Res.string.kitchen_paste))
        }
    }
}
