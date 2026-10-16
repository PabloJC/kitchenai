package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.profile_selected_count
import com.kitchenai.ui.resources.strength_avoid
import com.kitchenai.ui.resources.strength_choose
import com.kitchenai.ui.resources.strength_off
import com.kitchenai.ui.resources.strength_prefer
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One taxonomy, drawn from whatever the catalogue put in it. A section with no terms still
 * shows its header: an empty vocabulary is information, and hiding it looks like a bug.
 */
@Composable
fun ConstraintSection(
    section: ConstraintSectionUi,
    onChoose: (TermChipUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // What has been chosen, not how many terms exist: only the first tells the reader anything.
        val selected = section.terms.count { it.selected }
        SectionHeader(
            title = section.title,
            trailing =
                if (selected > 0) {
                    { Text(pluralStringResource(Res.plurals.profile_selected_count, selected, selected)) }
                } else {
                    null
                },
        )

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
            section.terms.forEach { chip -> ConstraintChip(chip = chip, onChoose = { onChoose(chip) }) }
        }
    }
}

/**
 * One target, not two: the whole chip opens the Prefer · Avoid · Off choice, so a near miss can no
 * longer turn an allergen off. Its strength is an icon, a colour and a spoken state, never a shade alone.
 */
@Composable
private fun ConstraintChip(
    chip: TermChipUi,
    onChoose: () -> Unit,
) {
    val strength = chip.strength
    val stateLabel = strength?.label() ?: stringResource(Res.string.strength_off)
    val chooseLabel = stringResource(Res.string.strength_choose)
    val tint = strength?.color()
    FilterChip(
        selected = chip.selected,
        onClick = onChoose,
        label = { Text(chip.label) },
        leadingIcon =
            strength?.let { held ->
                { Icon(held.icon(), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            },
        colors =
            if (tint == null) {
                FilterChipDefaults.filterChipColors()
            } else {
                FilterChipDefaults.filterChipColors(
                    selectedLeadingIconColor = tint,
                    selectedLabelColor = tint,
                )
            },
        modifier =
            Modifier.semantics {
                stateDescription = stateLabel
                onClick(label = chooseLabel) {
                    onChoose()
                    true
                }
            },
    )
}

// A strength is app logic rather than catalogue vocabulary, so its wording is ours to write —
// and therefore ours to translate.
@Composable
internal fun ConstraintStrength.label(): String =
    when (this) {
        ConstraintStrength.PREFER -> stringResource(Res.string.strength_prefer)
        ConstraintStrength.AVOID -> stringResource(Res.string.strength_avoid)
    }

private fun ConstraintStrength.icon(): ImageVector =
    when (this) {
        ConstraintStrength.PREFER -> Icons.Outlined.ThumbUp
        ConstraintStrength.AVOID -> Icons.Outlined.Block
    }

@Composable
private fun ConstraintStrength.color(): Color =
    when (this) {
        // Green and red, not a shade of the same chip: AVOID hard-filters a recipe out entirely
        // (#180), and reading like a lighter version of PREFER would be a safety problem.
        ConstraintStrength.PREFER -> MaterialTheme.colorScheme.tertiary
        ConstraintStrength.AVOID -> MaterialTheme.colorScheme.error
    }
