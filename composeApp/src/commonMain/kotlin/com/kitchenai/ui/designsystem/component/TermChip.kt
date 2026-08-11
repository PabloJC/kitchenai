package com.kitchenai.ui.designsystem.component

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Takes a label, not a term: the chip never learns what a taxonomy is. */
@Composable
fun TermChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(label) },
        modifier = modifier,
    )
}
