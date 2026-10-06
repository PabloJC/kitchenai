package com.kitchenai.ui.presentation.kitchen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.SectionHeader
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.navigation.DetailTopBarState
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.kitchen_copy
import com.kitchenai.ui.resources.kitchen_empty_body
import com.kitchenai.ui.resources.kitchen_empty_title
import com.kitchenai.ui.resources.kitchen_join_action
import com.kitchenai.ui.resources.kitchen_join_label
import com.kitchenai.ui.resources.kitchen_join_placeholder
import com.kitchenai.ui.resources.kitchen_leave_action
import com.kitchenai.ui.resources.kitchen_members_title
import com.kitchenai.ui.resources.kitchen_owner
import com.kitchenai.ui.resources.kitchen_regenerate_action
import com.kitchenai.ui.resources.kitchen_remove_action
import com.kitchenai.ui.resources.kitchen_title
import com.kitchenai.ui.resources.kitchen_you_suffix
import com.kitchenai.ui.resources.kitchen_your_code
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Membership as its own screen, reached from the profile: who is in the caller's kitchen, a code
 * to hand out, a field to join someone else's, and — for the owner alone — the two actions that
 * shape the group rather than just this viewer's place in it.
 */
@Composable
fun KitchenScreen(
    userId: UserId,
    detailTopBar: DetailTopBarState,
    modifier: Modifier = Modifier,
    viewModel: KitchenViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = stringResource(Res.string.kitchen_title)

    LaunchedEffect(userId) { viewModel.start(userId) }
    PublishTitle(title, detailTopBar)

    if (state.isLoading) {
        LoadingState(modifier)
    } else {
        KitchenContent(state = state, viewModel = viewModel, modifier = modifier)
    }
}

/** AppShell owns the one top bar every non-tab screen shares; this hands the title back on exit. */
@Composable
private fun PublishTitle(
    title: String,
    detailTopBar: DetailTopBarState,
) {
    DisposableEffect(title) {
        detailTopBar.title = title
        onDispose { detailTopBar.title = null }
    }
}

@Composable
private fun KitchenContent(
    state: KitchenUiState,
    viewModel: KitchenViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.large),
    ) {
        state.error?.let { message -> ErrorBanner(message) }

        val kitchen = state.kitchen
        if (kitchen != null) {
            JoinCodeSection(kitchen, isBusy = state.isBusy, onRegenerate = viewModel::regenerateJoinCode)
            MembersSection(kitchen, isBusy = state.isBusy, onRemove = viewModel::removeMember)
            LeaveSection(kitchen, isBusy = state.isBusy, onLeave = viewModel::leave)
        } else {
            EmptyState(
                title = stringResource(Res.string.kitchen_empty_title),
                body = stringResource(Res.string.kitchen_empty_body),
            )
        }

        JoinField(
            value = state.joinCodeInput,
            isBusy = state.isBusy,
            onChange = viewModel::onJoinCodeInputChange,
            onJoin = viewModel::join,
        )
    }
}

@Composable
private fun JoinCodeSection(
    kitchen: KitchenUi,
    isBusy: Boolean,
    onRegenerate: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
        SectionHeader(
            title = stringResource(Res.string.kitchen_your_code),
            trailing = {
                if (kitchen.isOwner) {
                    TextButton(onClick = onRegenerate, enabled = !isBusy) {
                        Text(stringResource(Res.string.kitchen_regenerate_action))
                    }
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
            horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(kitchen.joinCode, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(kitchen.joinCode)) }) {
                Text(stringResource(Res.string.kitchen_copy))
            }
        }
    }
}

@Composable
private fun MembersSection(
    kitchen: KitchenUi,
    isBusy: Boolean,
    onRemove: (UserId) -> Unit,
) {
    Column {
        SectionHeader(title = stringResource(Res.string.kitchen_members_title))
        kitchen.members.forEach { member -> MemberRow(member, isBusy, onRemove) }
    }
}

@Composable
private fun MemberRow(
    member: KitchenMemberUi,
    isBusy: Boolean,
    onRemove: (UserId) -> Unit,
) {
    val name = if (member.isSelf) stringResource(Res.string.kitchen_you_suffix, member.name) else member.name
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { if (member.isOwner) Text(stringResource(Res.string.kitchen_owner)) },
        trailingContent = {
            if (member.canRemove) {
                TextButton(onClick = { onRemove(member.id) }, enabled = !isBusy) {
                    Text(stringResource(Res.string.kitchen_remove_action))
                }
            }
        },
    )
}

@Composable
private fun LeaveSection(
    kitchen: KitchenUi,
    isBusy: Boolean,
    onLeave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
        kitchen.leaveDisabledReason?.let { reason -> ErrorBanner(reason, isError = false) }
        OutlinedButton(onClick = onLeave, enabled = kitchen.canLeave && !isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.kitchen_leave_action))
        }
    }
}

@Composable
private fun JoinField(
    value: String,
    isBusy: Boolean,
    onChange: (String) -> Unit,
    onJoin: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
        SectionHeader(title = stringResource(Res.string.kitchen_join_label))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
            horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text(stringResource(Res.string.kitchen_join_placeholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onJoin, enabled = !isBusy && value.isNotBlank()) {
                Text(stringResource(Res.string.kitchen_join_action))
            }
        }
    }
}

/** Above the content rather than replacing it: a stale kitchen still on screen is still useful. */
@Composable
private fun ErrorBanner(
    message: UiText,
    isError: Boolean = true,
) {
    Text(
        text = message.resolve(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}
