package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.theme.Dimens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where the user hands over their context. A profile that never loaded is a state drawn here,
 * not a crash, and an empty catalogue is an empty state rather than a list written in code.
 */
@Composable
fun ProfileScreen(
    userId: UserId,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(userId) { viewModel.start(userId) }

    val error = state.error
    when {
        !state.isLoading -> ProfileContent(state = state, viewModel = viewModel, modifier = modifier)
        error == null -> LoadingState(modifier)
        else -> ErrorState(message = error.message, modifier = modifier)
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.medium),
    ) {
        item { NameField(name = state.displayName, onChange = viewModel::setDisplayName) }
        item {
            ServingsStepper(
                servings = state.servings,
                error = state.errorFor(SERVINGS_FIELD),
                onChange = viewModel::setServings,
            )
        }
        item { TransparencyRow(state = state) }

        if (state.isCatalogueLoaded && state.sections.isEmpty()) {
            item {
                EmptyState(
                    title = "No preferences to show",
                    body =
                        if (state.hasCatalogueFailed) {
                            "The vocabulary could not be loaded, so there is nothing to choose from yet."
                        } else {
                            "This catalogue has no vocabulary in it yet."
                        },
                )
            }
        } else {
            item { state.errorFor(CONSTRAINTS_FIELD)?.let { message -> FieldMessage(message) } }
            items(state.sections, key = { section -> section.taxonomy.value }) { section ->
                ConstraintSection(
                    section = section,
                    onToggle = viewModel::toggleConstraint,
                    onCycleStrength = viewModel::cycleStrength,
                )
            }
        }

        item { SaveRow(isSaving = state.isSaving, message = state.generalError, onSave = viewModel::save) }
    }
}

@Composable
private fun NameField(
    name: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
    )
}

/** A stepper rather than a field: servings is a small count and a keyboard for it is a tax. */
@Composable
private fun ServingsStepper(
    servings: Int,
    error: String?,
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Servings", modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(servings - 1) }, enabled = servings > 1) { Text("-") }
            Text(text = servings.toString(), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onChange(servings + 1) }) { Text("+") }
        }
        error?.let { message -> FieldMessage(message) }
    }
}

/**
 * What leaves the device when a suggestion is asked for. Counts and tags only: a preferences
 * screen that hides what it sends is a dark pattern, and prose here would be a claim about the
 * data rather than the data.
 */
@Composable
private fun TransparencyRow(state: ProfileUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Sent with every suggestion")
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.extraSmall),
        ) {
            SentLine(label = "Constraints", value = state.constraintCount.toString())
            SentLine(label = "Servings", value = state.servings.toString())
            SentLine(label = "Languages", value = state.languageTags.joinToString())
            SentLine(label = "Pantry contents", value = "included")
        }
    }
}

@Composable
private fun SentLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SaveRow(
    isSaving: Boolean,
    message: String?,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        message?.let { text -> FieldMessage(text) }
        Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSaving) "Saving" else "Save")
        }
    }
}

/** In the error colour so it is not read as a hint. */
@Composable
private fun FieldMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
