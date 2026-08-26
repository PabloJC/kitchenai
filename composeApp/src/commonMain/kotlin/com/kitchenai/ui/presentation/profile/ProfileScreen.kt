package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.profile_no_preferences
import com.kitchenai.ui.resources.profile_no_vocabulary
import com.kitchenai.ui.resources.profile_save
import com.kitchenai.ui.resources.profile_saving
import com.kitchenai.ui.resources.profile_sent_summary
import com.kitchenai.ui.resources.profile_vocabulary_failed
import org.jetbrains.compose.resources.stringResource
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
        else -> ErrorState(message = error.message.resolve(), modifier = modifier)
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
        item { TransparencyLine() }

        if (state.isCatalogueLoaded && state.sections.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(Res.string.profile_no_preferences),
                    body =
                        if (state.hasCatalogueFailed) {
                            stringResource(Res.string.profile_vocabulary_failed)
                        } else {
                            stringResource(Res.string.profile_no_vocabulary)
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

/**
 * What leaves the device when a suggestion is asked for. One sentence rather than a panel of
 * counts: a number tells a reader nothing they can act on, and prose here would be a claim
 * about the data rather than the data — this names the categories, not amounts.
 */
@Composable
private fun TransparencyLine() {
    Text(
        text = stringResource(Res.string.profile_sent_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
    )
}

@Composable
private fun SaveRow(
    isSaving: Boolean,
    message: UiText?,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        message?.let { text -> FieldMessage(text) }
        Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSaving) stringResource(Res.string.profile_saving) else stringResource(Res.string.profile_save))
        }
    }
}

/** In the error colour so it is not read as a hint. */
@Composable
private fun FieldMessage(message: UiText) {
    Text(
        text = message.resolve(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
