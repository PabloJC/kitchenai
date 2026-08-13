package com.kitchenai.ui.designsystem.component

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Takes a label, not a term: the chip never learns what a taxonomy is. [trailing] is a slot
 * rather than a flag because what a selection means differs per screen.
 */
@Composable
fun TermChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(label) },
        modifier = modifier,
        trailingIcon = trailing,
    )
}
