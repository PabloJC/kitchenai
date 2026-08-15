package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.presentation.common.resolve

/**
 * One taxonomy, drawn from whatever the catalogue put in it. A section with no terms still
 * shows its header: an empty vocabulary is information, and hiding it looks like a bug.
 */
@Composable
fun ConstraintSection(
    section: ConstraintSectionUi,
    onToggle: (TermRef) -> Unit,
    onCycleStrength: (TermRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = section.title, trailing = { Text(section.terms.size.toString()) })

        section.error?.let { message ->
            Text(
                text = message.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Dimens.large),
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            verticalArrangement = Arrangement.spacedBy(Dimens.extraSmall),
        ) {
            section.terms.forEach { chip ->
                ConstraintChip(chip = chip, onToggle = onToggle, onCycleStrength = onCycleStrength)
            }
        }
    }
}

@Composable
private fun ConstraintChip(
    chip: TermChipUi,
    onToggle: (TermRef) -> Unit,
    onCycleStrength: (TermRef) -> Unit,
) {
    val marker: (@Composable () -> Unit)? =
        chip.strength?.let { strength ->
            { StrengthMarker(strength = strength, onCycle = { onCycleStrength(chip.term) }) }
        }

    TermChip(
        label = chip.label,
        selected = chip.selected,
        onToggle = { onToggle(chip.term) },
        trailing = marker,
    )
}

/**
 * The strength a selected term binds with, and the control that cycles it. An excluded term
 * and a preferred one reading alike is a safety problem, so it is a word and a colour rather
 * than a shade of the same chip.
 */
@Composable
private fun StrengthMarker(
    strength: ConstraintStrength,
    onCycle: () -> Unit,
) {
    Text(
        text = strength.label(),
        style = MaterialTheme.typography.labelMedium,
        color = strength.color(),
        modifier = Modifier.clickable(onClick = onCycle).padding(horizontal = Dimens.extraSmall),
    )
}

// Wording for a strength is safe to hold here: `ConstraintStrength` is app logic, not vocabulary.
private fun ConstraintStrength.label(): String =
    when (this) {
        ConstraintStrength.PREFER -> "Prefer"
        ConstraintStrength.AVOID -> "Avoid"
        ConstraintStrength.EXCLUDE -> "Exclude"
    }

@Composable
private fun ConstraintStrength.color(): Color =
    when (this) {
        ConstraintStrength.PREFER -> MaterialTheme.colorScheme.primary
        ConstraintStrength.AVOID -> MaterialTheme.colorScheme.tertiary
        ConstraintStrength.EXCLUDE -> MaterialTheme.colorScheme.error
    }
