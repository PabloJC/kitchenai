package com.kitchenai.ui.designsystem.input

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * A tap on the background clears focus, which closes the keyboard. Compose does neither by itself,
 * and on iOS the number pad otherwise has no way to go.
 */
@Composable
fun Modifier.closeKeyboardOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) { detectTapGestures { focusManager.clearFocus() } }
}

/** Escape on a hardware keyboard runs [onEscape], as a back gesture would. */
fun Modifier.onEscape(onEscape: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
        if (escape) onEscape()
        escape
    }
