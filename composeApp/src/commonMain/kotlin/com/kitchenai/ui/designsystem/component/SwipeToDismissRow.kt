package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * The delete gesture shared by the pantry and the shopping list. Swiping only from the end
 * side: the start side is where a back gesture lives on both platforms.
 *
 * [onDismiss] fires once the swipe is confirmed; removing the item is the caller's job. A swipe
 * is out of reach for TalkBack, VoiceOver and Switch Access, so the same delete is also offered
 * as an accessibility action named [deleteLabel]. [content] gets it as a modifier to put on its
 * own focusable node: a screen reader lists only the actions of the node it lands on.
 * [extraActions] join Delete there, for a row that can do more than go away.
 */
@Composable
fun SwipeToDismissRow(
    onDismiss: () -> Unit,
    deleteLabel: String,
    modifier: Modifier = Modifier,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
    content: @Composable (deleteAction: Modifier) -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val dismiss by rememberUpdatedState(onDismiss)

    // A tick when the swipe crosses the point where letting go deletes; backing off is silent.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.targetValue) {
        if (state.targetValue == SwipeToDismissBoxValue.EndToStart) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }

    // Reacting to the settled value instead of vetoing it: `confirmValueChange` is deprecated.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) dismiss()
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Trailing-aligned rather than centred: the swipe is fixed end-to-start, so this is
            // the edge the reveal grows from, and where a thumb mid-swipe is already looking.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(Dimens.corner))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = Dimens.large),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        // Opaque: the reveal is drawn behind it all the time, so a transparent row would show it.
        content = {
            val deleteAction =
                Modifier.semantics {
                    customActions =
                        extraActions +
                        CustomAccessibilityAction(deleteLabel) {
                            dismiss()
                            true
                        }
                }
            Box(Modifier.background(MaterialTheme.colorScheme.background)) { content(deleteAction) }
        },
    )
}
