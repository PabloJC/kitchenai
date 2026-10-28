package com.kitchenai.ui.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.NoFood
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.presentation.common.JoinCodeField
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.onboarding_add_count
import com.kitchenai.ui.resources.onboarding_back
import com.kitchenai.ui.resources.onboarding_have_code
import com.kitchenai.ui.resources.onboarding_join_body
import com.kitchenai.ui.resources.onboarding_join_title
import com.kitchenai.ui.resources.onboarding_next
import com.kitchenai.ui.resources.onboarding_not_now
import com.kitchenai.ui.resources.onboarding_preferences_action
import com.kitchenai.ui.resources.onboarding_preferences_body
import com.kitchenai.ui.resources.onboarding_preferences_title
import com.kitchenai.ui.resources.onboarding_skip
import com.kitchenai.ui.resources.onboarding_staples_body
import com.kitchenai.ui.resources.onboarding_staples_title
import com.kitchenai.ui.resources.onboarding_step
import com.kitchenai.ui.resources.onboarding_value_body
import com.kitchenai.ui.resources.onboarding_value_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Drawn before the shell exists, so it pads for the system bars itself, as SessionGate does. */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step != OnboardingStep.VALUE) {
                TextButton(onClick = viewModel::back) { Text(stringResource(Res.string.onboarding_back)) }
            }
            val page = OnboardingStep.pages.indexOf(state.step)
            Text(
                if (page >= 0) stringResource(Res.string.onboarding_step, page + 1, OnboardingStep.pages.size) else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = Dimens.small),
            )
            TextButton(onClick = viewModel::skip) { Text(stringResource(Res.string.onboarding_skip)) }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.large),
        ) {
            StepContent(state, viewModel)
            state.error?.let { message ->
                Text(
                    message.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Actions(state, viewModel)
    }
}

/** What the current page shows above the error line. */
@Composable
private fun StepContent(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    when (state.step) {
        OnboardingStep.VALUE ->
            Page(Icons.Filled.AutoAwesome, Res.string.onboarding_value_title, Res.string.onboarding_value_body)
        OnboardingStep.STAPLES -> {
            Page(
                Icons.Outlined.Kitchen,
                Res.string.onboarding_staples_title,
                Res.string.onboarding_staples_body,
            )
            Staples(state.staples, onToggle = viewModel::toggle)
        }
        OnboardingStep.PREFERENCES ->
            Page(
                Icons.Outlined.NoFood,
                Res.string.onboarding_preferences_title,
                Res.string.onboarding_preferences_body,
            )
        OnboardingStep.JOIN -> {
            Page(Icons.Outlined.Group, Res.string.onboarding_join_title, Res.string.onboarding_join_body)
            JoinCodeField(
                value = state.joinCode,
                isBusy = state.isBusy,
                onChange = viewModel::onJoinCodeChange,
                onPaste = viewModel::pasteJoinCode,
                onJoin = viewModel::join,
            )
        }
    }
}

@Composable
private fun Actions(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        if (state.step == OnboardingStep.JOIN) return@Column
        if (state.step == OnboardingStep.PREFERENCES) {
            Button(onClick = viewModel::setPreferences, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.onboarding_preferences_action))
            }
            TextButton(onClick = viewModel::next, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.onboarding_not_now))
            }
        } else {
            val label =
                if (state.step == OnboardingStep.STAPLES && state.chosenCount > 0) {
                    pluralStringResource(Res.plurals.onboarding_add_count, state.chosenCount, state.chosenCount)
                } else {
                    stringResource(Res.string.onboarding_next)
                }
            Button(onClick = viewModel::next, enabled = !state.isAdding, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
        }
        // The first page only: someone with an account or an invite has no staples to add here.
        if (state.step == OnboardingStep.VALUE) {
            OnboardingSignIn(state, viewModel)
            TextButton(onClick = viewModel::showJoin, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.onboarding_have_code))
            }
        }
    }
}

@Composable
private fun Page(
    icon: ImageVector,
    title: StringResource,
    body: StringResource,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.medium)) {
        // Decorative: the title right below says the same thing in words.
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Staples(
    staples: List<StapleUi>,
    onToggle: (IngredientId) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        staples.forEach { staple ->
            FilterChip(
                selected = staple.isChosen,
                onClick = { onToggle(staple.id) },
                label = { Text(staple.label) },
            )
        }
    }
}

private val ICON_SIZE = 56.dp
