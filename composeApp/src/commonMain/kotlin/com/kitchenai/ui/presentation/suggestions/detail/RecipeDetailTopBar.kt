package com.kitchenai.ui.presentation.suggestions.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.kitchenai.ui.platform.backIcon
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_save
import com.kitchenai.ui.resources.detail_share
import com.kitchenai.ui.resources.detail_unsave
import com.kitchenai.ui.resources.nav_back
import org.jetbrains.compose.resources.stringResource

/**
 * Transparent over the photo and solid once content scrolls under it; the title only appears
 * once the header's own title has scrolled away, so it is on screen exactly once. Over the photo each icon sits on a
 * tonal disc, since a photograph gives no colour a bare icon can count on contrasting with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecipeDetailTopBar(
    solid: Boolean,
    titled: Boolean,
    state: RecipeDetailUiState,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    onShare: ((anchor: Rect?) -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val buttonColors =
        if (solid) IconButtonDefaults.iconButtonColors() else IconButtonDefaults.filledTonalIconButtonColors()
    TopAppBar(
        title = {
            AnimatedVisibility(visible = titled, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    state.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack, colors = buttonColors) {
                Icon(backIcon, contentDescription = stringResource(Res.string.nav_back))
            }
        },
        actions = {
            onShare?.let { share ->
                // Read only on click, so a layout pass writing it never recomposes the bar.
                var anchor by remember { mutableStateOf<Rect?>(null) }
                IconButton(
                    onClick = { share(anchor) },
                    colors = buttonColors,
                    modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() },
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(Res.string.detail_share))
                }
            }
            SavedToggle(state, solid, onToggleSaved)
        },
        // AppShell already consumed the status bar; a second round would push the bar down.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = if (solid) colors.surface else Color.Transparent,
            ),
    )
}

/** On/off rather than a plain button, so TalkBack and VoiceOver announce whether it is kept. */
@Composable
private fun SavedToggle(
    state: RecipeDetailUiState,
    solid: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(if (state.isSaved) Res.string.detail_unsave else Res.string.detail_save)
    val haptics = LocalHapticFeedback.current
    IconToggleButton(
        checked = state.isSaved,
        onCheckedChange = { saving ->
            // Only keeping it is confirmed; removing is undone from the snackbar, not celebrated.
            if (saving) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onToggle()
        },
        enabled = !state.isWorking && state.title.isNotEmpty(),
        colors =
            if (solid) {
                IconButtonDefaults.iconToggleButtonColors(checkedContentColor = MaterialTheme.colorScheme.primary)
            } else {
                IconButtonDefaults.filledTonalIconToggleButtonColors(
                    checkedContentColor = MaterialTheme.colorScheme.primary,
                )
            },
    ) {
        Icon(if (state.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = label)
    }
}
