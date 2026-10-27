package com.kitchenai.ui.presentation.shopping

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * The snackbar after a move, with two actions where Material gives one: [actionLabel] (Undo) is
 * the standard action, so `showSnackbar` still reports it; [secondaryLabel] runs [onSecondary].
 */
internal class MovedToPantryVisuals(
    override val message: String,
    override val actionLabel: String,
    val secondaryLabel: String,
    val onSecondary: () -> Unit,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

/** Draws [MovedToPantryVisuals] with both buttons and every other snackbar as Material does. */
@Composable
internal fun ShoppingSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        val visuals = data.visuals
        if (visuals !is MovedToPantryVisuals) {
            Snackbar(data)
            return@SnackbarHost
        }
        val actionColors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)
        // The padding the one-action overload adds by itself.
        Snackbar(
            modifier = Modifier.padding(Dimens.medium),
            actionOnNewLine = true,
            action = {
                Row {
                    TextButton(
                        onClick = {
                            data.dismiss()
                            visuals.onSecondary()
                        },
                        colors = actionColors,
                    ) { Text(visuals.secondaryLabel) }
                    TextButton(onClick = data::performAction, colors = actionColors) { Text(visuals.actionLabel) }
                }
            },
        ) { Text(visuals.message) }
    }
}
