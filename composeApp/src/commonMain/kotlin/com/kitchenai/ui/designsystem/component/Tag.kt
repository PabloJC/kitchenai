package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.designsystem.theme.PillShape

/**
 * A read-only pill of coloured text: an ingredient's pantry status, a recipe's tag, or — with
 * [icon] — a small marker like "Generated". Never clickable — [TermChip] is the one that toggles.
 */
@Composable
fun Tag(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = PillShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.small, vertical = Dimens.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) }
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
