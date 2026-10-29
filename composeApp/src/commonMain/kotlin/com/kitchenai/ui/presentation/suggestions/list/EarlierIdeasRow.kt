package com.kitchenai.ui.presentation.suggestions.list

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.suggestions_earlier
import com.kitchenai.ui.resources.suggestions_earlier_older
import com.kitchenai.ui.resources.suggestions_earlier_position
import com.kitchenai.ui.resources.suggestions_latest
import org.jetbrains.compose.resources.stringResource

/**
 * One line above the cards. On the latest ideas it offers the set before them; on an earlier set
 * it says which one is showing, pages further back and returns to the latest.
 */
@Composable
internal fun EarlierIdeasRow(
    earlier: List<List<SuggestionUi>>,
    browsing: Int?,
    onBrowse: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (browsing == null) {
        TextButton(onClick = { onBrowse(0) }, modifier = modifier) {
            Icon(Icons.Outlined.History, contentDescription = null)
            Text(
                stringResource(Res.string.suggestions_earlier, earlier.first().size),
                modifier = Modifier.padding(start = Dimens.small),
            )
        }
        return
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.suggestions_earlier_position, browsing + 1, earlier.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (browsing + 1 < earlier.size) {
            TextButton(
                onClick = { onBrowse(browsing + 1) },
            ) { Text(stringResource(Res.string.suggestions_earlier_older)) }
        }
        TextButton(onClick = { onBrowse(null) }) { Text(stringResource(Res.string.suggestions_latest)) }
    }
}
