package com.kitchenai.ui.presentation.suggestions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
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

/** Local to this screen on purpose: the shared error component is a separate change. */
@Composable
internal fun SuggestionsErrorCard(
    message: UiText,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            Text(message.resolve(), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.suggestions_retry)) }
        }
    }
}
