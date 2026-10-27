package com.kitchenai.ui.presentation.suggestions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.component.ErrorBanner
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.network_new_ideas_offline
import com.kitchenai.ui.resources.suggestions_cancel
import com.kitchenai.ui.resources.suggestions_filters_changed
import com.kitchenai.ui.resources.suggestions_retry
import com.kitchenai.ui.resources.suggestions_update_ideas
import com.kitchenai.ui.resources.suggestions_working
import org.jetbrains.compose.resources.stringResource

/** Shown whenever a run is in flight, over existing results or not, so it can always be stopped. */
@Composable
internal fun GeneratingStrip(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            Text(
                stringResource(Res.string.suggestions_working),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.suggestions_cancel)) }
        }
    }
}

/** Offered, never run on its own: each run costs a model call. */
@Composable
internal fun FiltersChangedBanner(
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        Text(
            stringResource(Res.string.suggestions_filters_changed),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onUpdate) { Text(stringResource(Res.string.suggestions_update_ideas)) }
    }
}

/**
 * The shared banner, with a Try again that reopens whatever failed: the kitchen or the last run.
 * Offline it has none: the offline note says why, and a button that cannot run would look broken.
 */
@Composable
internal fun SuggestionsErrorBanner(
    message: UiText,
    onRetry: () -> Unit,
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    ErrorBanner(
        message = message.resolve(),
        modifier = modifier,
        retryLabel = if (online) stringResource(Res.string.suggestions_retry) else null,
        onRetry = onRetry.takeIf { online },
    )
}

/** Why New ideas is disabled, said once above the results rather than left to a greyed button. */
@Composable
internal fun OfflineNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.network_new_ideas_offline),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}
