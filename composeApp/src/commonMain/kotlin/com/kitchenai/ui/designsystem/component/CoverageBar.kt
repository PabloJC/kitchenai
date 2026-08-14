package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * [fraction] arrives already in 0..1, from `coverageFraction`: the division belongs outside
 * the composable, where it can be tested.
 *
 * The colours are set rather than inherited. Material 3 draws its track at full width and adds
 * a stop indicator at the end, which over this palette made an empty bar read as a full one —
 * a recipe covering none of its ingredients looked like a recipe covering all of them.
 */
@Composable
fun CoverageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.extraSmall),
    ) {
        label?.invoke()
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            // Everything held turns the bar green: the one state worth recognising without
            // reading the number beside it.
            color = if (fraction >= 1f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            // outlineVariant, not surfaceVariant: the latter is the card's own colour here, so
            // the empty part of the bar vanished into it and left nothing to read at all.
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            // The dot sits at the far end whatever the progress, which is the other half of why
            // an empty bar looked finished.
            drawStopIndicator = {},
        )
    }
}
