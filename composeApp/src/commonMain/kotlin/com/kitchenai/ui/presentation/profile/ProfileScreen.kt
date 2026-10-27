package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Appearance
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.ErrorBanner
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.AppleSignInLauncher
import com.kitchenai.ui.platform.GoogleSignInLauncher
import com.kitchenai.ui.platform.rememberAppleSignInLauncher
import com.kitchenai.ui.platform.rememberGoogleSignInLauncher
import com.kitchenai.ui.presentation.common.AppearanceUiState
import com.kitchenai.ui.presentation.common.AppearanceViewModel
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_retry
import com.kitchenai.ui.resources.profile_delete_account
import com.kitchenai.ui.resources.profile_delete_body
import com.kitchenai.ui.resources.profile_delete_cancel
import com.kitchenai.ui.resources.profile_delete_confirm
import com.kitchenai.ui.resources.profile_delete_title
import com.kitchenai.ui.resources.profile_group_about
import com.kitchenai.ui.resources.profile_group_account
import com.kitchenai.ui.resources.profile_group_appearance
import com.kitchenai.ui.resources.profile_group_food
import com.kitchenai.ui.resources.profile_group_household
import com.kitchenai.ui.resources.profile_group_privacy
import com.kitchenai.ui.resources.profile_group_reminders
import com.kitchenai.ui.resources.profile_no_preferences
import com.kitchenai.ui.resources.profile_no_vocabulary
import com.kitchenai.ui.resources.profile_saved
import com.kitchenai.ui.resources.profile_saving
import com.kitchenai.ui.resources.profile_sent_summary
import com.kitchenai.ui.resources.profile_switch_body
import com.kitchenai.ui.resources.profile_switch_cancel
import com.kitchenai.ui.resources.profile_switch_confirm
import com.kitchenai.ui.resources.profile_switch_title
import com.kitchenai.ui.resources.profile_vocabulary_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where the user hands over their context. A profile that never loaded is a state drawn here,
 * not a crash, and an empty catalogue is an empty state rather than a list written in code.
 */
@Composable
fun ProfileScreen(
    userId: UserId,
    onOpenKitchen: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
    appearanceViewModel: AppearanceViewModel = koinViewModel(),
    reminderViewModel: ReminderSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(userId) { viewModel.start(userId) }

    val error = state.error
    when {
        !state.isLoading ->
            ProfileContent(
                state = state,
                viewModel = viewModel,
                appearance = appearance,
                onAppearance = appearanceViewModel::choose,
                reminders = reminderViewModel,
                onOpenKitchen = onOpenKitchen,
                modifier = modifier,
            )
        error == null -> LoadingState(modifier)
        else ->
            ErrorState(
                message = error.message.resolve(),
                modifier = modifier,
                retryLabel = stringResource(Res.string.error_retry),
                onRetry = viewModel::retry,
            )
    }
}

/**
 * Grouped like the platforms' own settings: Account, Household, Food preferences, Appearance,
 * Reminders, Privacy, About. One scroll for all of them, with the save status pinned below it.
 */
@Composable
private fun ProfileContent(
    state: ProfileUiState,
    viewModel: ProfileViewModel,
    appearance: AppearanceUiState,
    onAppearance: (Appearance) -> Unit,
    reminders: ReminderSettingsViewModel,
    onOpenKitchen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reminderState by reminders.state.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf<TermRef?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
            item { GroupTitle(Res.string.profile_group_account) }
            item { AccountArea(state, viewModel) }
            item { GroupTitle(Res.string.profile_group_household) }
            item { KitchenRow(onOpenKitchen) }
            item { GroupTitle(Res.string.profile_group_food) }
            foodPreferences(state, viewModel) { term -> choosing = term }
            item { GroupTitle(Res.string.profile_group_appearance) }
            item { AppearanceChooser(appearance, onAppearance) }
            item { GroupTitle(Res.string.profile_group_reminders) }
            item { ReminderRows(reminderState, reminders) }
            item { GroupTitle(Res.string.profile_group_privacy) }
            item { TransparencyLine() }
            if (state.hasAccount) {
                item { DeleteAccountAction(enabled = !state.isAuthenticating, onDelete = viewModel::deleteAccount) }
            }
            item { GroupTitle(Res.string.profile_group_about) }
            item { VersionRow() }
        }
        SaveStatus(
            isSaving = state.isSaving || state.hasUnsavedChanges,
            isSaved = state.isSaved,
            message = state.generalError,
            onRetry = if (state.canRetry) viewModel::retry else null,
        )
        Spacer(Modifier.height(Dimens.large))
    }
    // Read from state each time, so the sheet shows the strength just chosen rather than the one it opened with.
    val chip = choosing?.let { term -> state.sections.flatMap { it.terms }.firstOrNull { it.term == term } }
    if (chip != null) {
        StrengthSheet(
            chip = chip,
            onChoose = { strength ->
                viewModel.setStrength(chip.term, strength)
                choosing = null
            },
            onDismiss = { choosing = null },
        )
    }
}

/** The constraint sections, or what to say when the catalogue has none to offer. */
private fun LazyListScope.foodPreferences(
    state: ProfileUiState,
    viewModel: ProfileViewModel,
    onChoose: (TermRef) -> Unit,
) {
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
                actionLabel = if (state.hasCatalogueFailed) stringResource(Res.string.error_retry) else null,
                onAction = if (state.hasCatalogueFailed) viewModel::retry else null,
            )
        }
        return
    }
    items(state.sections, key = { section -> section.taxonomy.value }) { section ->
        ConstraintSection(section = section, onChoose = { chip -> onChoose(chip.term) })
    }
}

/** The sign-in buttons or the signed-in row, the platform sheets behind them, and the switch question. */
@Composable
private fun AccountArea(
    state: ProfileUiState,
    viewModel: ProfileViewModel,
) {
    val launcher = rememberGoogleSignInLauncher()
    val appleLauncher = rememberAppleSignInLauncher()
    val scope = rememberCoroutineScope()

    // isAuthenticating alone lags a whole async window behind: it only flips once
    // viewModel.signInWithGoogle() is called, which is after launcher.launch()'s platform
    // credential sheet has already returned. This closes that window so a second tap during
    // it cannot stack a second concurrent credential sheet.
    var signInLaunching by remember { mutableStateOf(false) }

    // The first Apple sign-in and a confirmed Apple switch both open the sheet, behind the same guard.
    val launchApple: ((AppleIdToken, AppleRawNonce, String?) -> Unit) -> Unit = { onToken ->
        if (appleLauncher != null && !signInLaunching) {
            signInLaunching = true
            scope.launch { performAppleSignIn(appleLauncher, viewModel, onToken) { signInLaunching = false } }
        }
    }

    AccountSection(
        hasAccount = state.hasAccount,
        displayName = state.displayName,
        isAuthenticating = state.isAuthenticating || signInLaunching,
        onSignIn = {
            if (signInLaunching) return@AccountSection
            signInLaunching = true
            scope.launch { performGoogleSignIn(launcher, viewModel) { signInLaunching = false } }
        },
        onAppleSignIn = appleLauncher?.let { { launchApple(viewModel::signInWithApple) } },
        onSignOut = viewModel::signOut,
    )

    if (state.isConfirmingAccountSwitch) {
        SwitchAccountDialog(
            onConfirm = {
                // Apple's nonce went with the refused link, so its sheet answers again before the switch.
                if (state.switchNeedsAppleSheet) {
                    viewModel.cancelAccountSwitch()
                    launchApple(viewModel::switchToAppleAccount)
                } else {
                    viewModel.confirmAccountSwitch()
                }
            },
            onDismiss = viewModel::cancelAccountSwitch,
        )
    }
}

/** Destructive and final, so it sits apart from Sign out, in the error colour, and always asks first. */
@Composable
private fun DeleteAccountAction(
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { confirming = true },
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.padding(horizontal = Dimens.large),
    ) { Text(stringResource(Res.string.profile_delete_account)) }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.profile_delete_title)) },
            text = { Text(stringResource(Res.string.profile_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirming = false
                        onDelete()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) { Text(stringResource(Res.string.profile_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(Res.string.profile_delete_cancel)) }
            },
        )
    }
}

/** Switching leaves the guest data behind, so it is never the default: dismissing keeps the user a guest. */
@Composable
private fun SwitchAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.profile_switch_title)) },
        text = { Text(stringResource(Res.string.profile_switch_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.profile_switch_confirm)) } },
        dismissButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.profile_switch_cancel)) } },
    )
}

/** [onFinished] always runs, cancelled or not, so a stuck sheet can never leave the button disabled forever. */
private suspend fun performGoogleSignIn(
    launcher: GoogleSignInLauncher,
    viewModel: ProfileViewModel,
    onFinished: () -> Unit,
) {
    try {
        when (val result = launcher.launch()) {
            is AppResult.Success ->
                viewModel.signInWithGoogle(result.data.token, result.data.displayName, result.data.accessToken)
            is AppResult.Failure -> viewModel.onSignInSheetFailed(result.error)
        }
    } finally {
        onFinished()
    }
}

/** [performGoogleSignIn] for Apple; [onToken] is the first sign-in or the confirmed switch. */
private suspend fun performAppleSignIn(
    launcher: AppleSignInLauncher,
    viewModel: ProfileViewModel,
    onToken: (AppleIdToken, AppleRawNonce, String?) -> Unit,
    onFinished: () -> Unit,
) {
    try {
        when (val result = launcher.launch()) {
            is AppResult.Success -> onToken(result.data.token, result.data.rawNonce, result.data.displayName)
            is AppResult.Failure -> viewModel.onSignInSheetFailed(result.error)
        }
    } finally {
        onFinished()
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

/**
 * Where the Save button was: a quiet "Saving" or "Saved", since every change is written on its
 * own; a refused write shows here with Retry, its chip already back to what is stored.
 */
@Composable
private fun SaveStatus(
    isSaving: Boolean,
    isSaved: Boolean,
    message: UiText?,
    onRetry: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        when {
            message != null ->
                ErrorBanner(message.resolve(), retryLabel = stringResource(Res.string.error_retry), onRetry = onRetry)
            isSaving || isSaved ->
                Text(
                    text = stringResource(if (isSaving) Res.string.profile_saving else Res.string.profile_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
        }
    }
}
