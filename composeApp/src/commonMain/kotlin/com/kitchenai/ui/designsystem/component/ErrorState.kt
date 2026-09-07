package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * [message] is already a sentence: turning an `AppError` into wording is the ViewModel's job,
 * which keeps this component free of domain types and the wording translatable.
 *
 * The retry button needs both a label and an action; a button with no wording is not one.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    // Centred rather than merely spaced: a caller that hands this the whole screen's height
    // means the message to land in the middle of it, the way LoadingState does a moment earlier
    // in the same slot. Sizing stays the caller's own modifier — see EmptyState for why.
    Column(
        modifier = modifier.fillMaxWidth().padding(Dimens.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.medium, Alignment.CenterVertically),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )

        if (retryLabel != null && onRetry != null) {
            Button(onClick = onRetry) { Text(retryLabel) }
        }
    }
}
