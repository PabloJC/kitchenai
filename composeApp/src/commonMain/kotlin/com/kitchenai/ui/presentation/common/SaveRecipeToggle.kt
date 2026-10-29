package com.kitchenai.ui.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_save
import com.kitchenai.ui.resources.detail_unsave
import org.jetbrains.compose.resources.stringResource

/**
 * The heart on a recipe, wherever one is drawn. On/off rather than a plain button, so TalkBack
 * and VoiceOver announce whether it is kept.
 */
@Composable
fun SaveRecipeToggle(
    saved: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors =
        IconButtonDefaults.iconToggleButtonColors(checkedContentColor = MaterialTheme.colorScheme.primary),
) {
    val label = stringResource(if (saved) Res.string.detail_unsave else Res.string.detail_save)
    val haptics = LocalHapticFeedback.current
    IconToggleButton(
        checked = saved,
        onCheckedChange = { saving ->
            // Only keeping it is confirmed; removing is undone from the snackbar, not celebrated.
            if (saving) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onToggle()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    ) {
        Icon(if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = label)
    }
}
