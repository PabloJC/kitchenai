package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * The delete gesture shared by the pantry and the shopping list. Swiping only from the end
 * side: the start side is where a back gesture lives on both platforms.
 *
 * [onDismiss] fires once the swipe is confirmed; removing the item is the caller's job.
 */
@Composable
fun SwipeToDismissRow(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val dismiss by rememberUpdatedState(onDismiss)

    // Reacting to the settled value instead of vetoing it: `confirmValueChange` is deprecated.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) dismiss()
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(Dimens.corner))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = Dimens.large),
            )
        },
        content = { content() },
    )
}
