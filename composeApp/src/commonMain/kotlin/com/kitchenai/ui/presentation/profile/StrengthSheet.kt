package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.strength_avoid_explained
import com.kitchenai.ui.resources.strength_off
import com.kitchenai.ui.resources.strength_prefer_explained
import org.jetbrains.compose.resources.stringResource

/**
 * The one place a term's strength is chosen, with what each choice does spelled out: Avoid
 * removes recipes outright (#180), and for an allergy that has to be read, not guessed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StrengthSheet(
    chip: TermChipUi,
    onChoose: (ConstraintStrength?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(ConstraintStrength.PREFER, ConstraintStrength.AVOID, null)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(
                    start = Dimens.large,
                    end = Dimens.large,
                    bottom = Dimens.extraLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            Text(chip.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = chip.strength == option,
                        onClick = { onChoose(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(option?.label() ?: stringResource(Res.string.strength_off)) }
                }
            }
            Text(stringResource(Res.string.strength_prefer_explained), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.strength_avoid_explained),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
