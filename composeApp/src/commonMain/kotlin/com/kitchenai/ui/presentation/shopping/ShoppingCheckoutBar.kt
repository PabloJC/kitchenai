package com.kitchenai.ui.presentation.shopping

import androidx.compose.foundation.layout.Column
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The step that closes the loop, pinned above the add field while anything is ticked. Every
 * ticked line moves: one with no amount goes as one, and the review sheet shows it.
 */
@Composable
fun ShoppingCheckoutBar(
    movableCount: Int,
    onMoveToPantry: () -> Unit,
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
        TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.shopping_clear))
        }
    }
}
