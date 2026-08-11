package com.kitchenai.ui.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitchenai.ui.designsystem.component.CoverageBar
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.QuantityField
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.component.SwipeToDismissRow
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.designsystem.theme.KitchenAiTheme

// Previews live in androidMain because Compose Multiplatform renders them on Android only.
// Keeping them out of commonMain also keeps the tooling annotation off the iOS classpath.

@Preview
@Composable
private fun StatesPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.large)) {
            ErrorState(
                message = "No connection to Firebase",
                retryLabel = "Retry",
                onRetry = {},
            )
            EmptyState(
                title = "Nothing in the pantry",
                body = "Add what you already have and the suggestions will follow.",
                actionLabel = "Add an item",
                onAction = {},
            )
        }
    }
}

@Preview
@Composable
private fun LoadingStatePreview() {
    PreviewSurface { LoadingState() }
}

@Preview
@Composable
private fun QuantityFieldPreview() {
    PreviewSurface {
        QuantityField(
            amountLabel = "Amount",
            units = listOf("g" to "g", "ml" to "ml", "unit" to "units"),
            onChange = { _, _ -> },
            initialAmount = "1,5",
        )
    }
}

@Preview
@Composable
private fun TermChipPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
            TermChip(label = "Vegetarian", selected = true, onToggle = {})
            TermChip(label = "Gluten free", selected = false, onToggle = {})
        }
    }
}

@Preview
@Composable
private fun SectionAndCoveragePreview() {
    PreviewSurface {
        Column {
            SectionHeader(title = "Suggestions", trailing = { Text("3") })
            CoverageBar(
                fraction = 0.6f,
                label = { Text("6 of 10 ingredients", style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Preview
@Composable
private fun SwipeToDismissRowPreview() {
    PreviewSurface {
        SwipeToDismissRow(onDismiss = {}) {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Text("Tomatoes", modifier = Modifier.padding(Dimens.large))
            }
        }
    }
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    KitchenAiTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(Dimens.large)) { content() }
        }
    }
}
