package com.kitchenai.ui.presentation.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.shopping_clear
import com.kitchenai.ui.resources.shopping_move_count
import com.kitchenai.ui.resources.shopping_move_to_pantry
import com.kitchenai.ui.resources.shopping_need_amount_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The step that closes the loop, pinned above the add field while anything is ticked.
 *
 * The count on the button is what will actually move; lines without an amount are named beneath
 * it and that note is a button, so the fix is one tap away rather than a snackbar after the fact.
 */
@Composable
fun ShoppingCheckoutBar(
    movableCount: Int,
    missingAmountCount: Int,
    onMoveToPantry: () -> Unit,
    onFixAmounts: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.large, vertical = Dimens.small)) {
        Button(
            onClick = onMoveToPantry,
            enabled = movableCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (movableCount > 0) {
                    pluralStringResource(Res.plurals.shopping_move_count, movableCount, movableCount)
                } else {
                    stringResource(Res.string.shopping_move_to_pantry)
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (missingAmountCount > 0) {
                TextButton(onClick = onFixAmounts) {
                    Text(
                        pluralStringResource(
                            Res.plurals.shopping_need_amount_count,
                            missingAmountCount,
                            missingAmountCount,
                        ),
                    )
                }
            } else {
                Spacer(Modifier)
            }
            TextButton(onClick = onClear) { Text(stringResource(Res.string.shopping_clear)) }
        }
    }
}
