package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kitchenai.ui.designsystem.theme.Dimens

/**
 * A read-only pill of coloured text: an ingredient's pantry status, or a recipe's tag.
 * Never clickable — [TermChip] is the one that toggles.
 */
@Composable
fun Tag(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Dimens.small, vertical = Dimens.extraSmall),
        )
    }
}
