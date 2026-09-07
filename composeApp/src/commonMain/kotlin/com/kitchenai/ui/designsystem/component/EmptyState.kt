package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kitchenai.ui.designsystem.theme.Dimens

/** Every string arrives as a parameter; the component decides layout, never wording. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // Centred rather than merely spaced: a caller that hands this the whole screen's height
    // (the ordinary case — a pantry, a list, a catalogue with nothing in it) means the message
    // to land in the middle of it, the way LoadingState does a moment earlier in the same slot.
    // Sizing stays the caller's own modifier: forcing fillMaxSize here would break the callers
    // that place this inside a LazyColumn item, where the incoming height is unbounded.
    Column(
        modifier = modifier.fillMaxWidth().padding(Dimens.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.small, Alignment.CenterVertically),
    ) {
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
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
