package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * Every string arrives as a parameter; the component decides layout, never wording. The action is
 * a tonal button, not a text one: on an empty screen it is the main thing there is to do.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // Centred to match LoadingState, the same slot a moment earlier; sizing is left to the
    // caller, since forcing it here would break a caller with unbounded height (a lazy item).
    Column(
        modifier = modifier.fillMaxWidth().padding(Dimens.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.small, Alignment.CenterVertically),
    ) {
        icon?.let {
            // Decorative: the title right below says the same thing in words.
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = Dimens.small)) { Text(actionLabel) }
        }
    }
}

private val ICON_SIZE = 48.dp
