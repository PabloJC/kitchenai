package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * [fraction] arrives already in 0..1, from `coverageFraction`: the division belongs outside
 * the composable, where it can be tested.
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
        )
    }
}
