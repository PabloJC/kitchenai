package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kitchenai.shared.domain.model.Appearance
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.di.PRIVACY_POLICY_URL
import com.kitchenai.ui.platform.isWebLink
import com.kitchenai.ui.platform.rememberAppVersion
import com.kitchenai.ui.platform.rememberOpenUrl
import com.kitchenai.ui.presentation.common.AppearanceUiState
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.appearance_dark
import com.kitchenai.ui.resources.appearance_light
import com.kitchenai.ui.resources.appearance_system
import com.kitchenai.ui.resources.display_name_title
import com.kitchenai.ui.resources.display_name_unset
import com.kitchenai.ui.resources.profile_kitchen_body
import com.kitchenai.ui.resources.profile_kitchen_title
import com.kitchenai.ui.resources.profile_licences
import com.kitchenai.ui.resources.profile_licences_body
import com.kitchenai.ui.resources.profile_photo_credits
import com.kitchenai.ui.resources.profile_photo_credits_body
import com.kitchenai.ui.resources.profile_privacy_policy
import com.kitchenai.ui.resources.profile_privacy_policy_body
import com.kitchenai.ui.resources.profile_version
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** A group's name, in the primary colour like the platforms' settings, and a heading for screen readers. */
@Composable
internal fun GroupTitle(title: StringResource) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Dimens.large, end = Dimens.large, top = Dimens.extraLarge)
                .semantics { heading() },
    )
}

/** Sharing a kitchen is a feature, not a footnote: a full row leading to it rather than a text link. */
@Composable
internal fun KitchenRow(onOpen: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.profile_kitchen_title)) },
        supportingContent = { Text(stringResource(Res.string.profile_kitchen_body)) },
        leadingContent = { Icon(Icons.Outlined.Groups, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
    )
}

/** For guests too: the name is what the rest of the kitchen sees, whoever signed in or not. */
@Composable
internal fun DisplayNameRow(
    name: String?,
    onEdit: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.display_name_title)) },
        supportingContent = { Text(name ?: stringResource(Res.string.display_name_unset)) },
        leadingContent = { Icon(Icons.Outlined.Badge, contentDescription = null) },
        trailingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onEdit),
    )
}

/** Kept on this device: the system's own choice by default, forced light or dark when asked. */
@Composable
internal fun AppearanceChooser(
    state: AppearanceUiState,
    onChoose: (Appearance) -> Unit,
) {
    val options = Appearance.entries
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = state.appearance == option,
                    onClick = { onChoose(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(stringResource(option.label())) }
            }
        }
        state.error?.let { message ->
            Text(
                message.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Dimens.small),
            )
        }
    }
}

/** The privacy policy (hidden until build configuration names one), photo credits and open-source licences. */
@Composable
internal fun AboutRows(navigation: ProfileNavigation) {
    val policy = koinInject<String>(PRIVACY_POLICY_URL)
    val openUrl = rememberOpenUrl()
    Column {
        if (isWebLink(policy)) {
            AboutRow(Res.string.profile_privacy_policy, Res.string.profile_privacy_policy_body, Icons.Outlined.Policy) {
                openUrl(policy)
            }
        }
        AboutRow(Res.string.profile_photo_credits, Res.string.profile_photo_credits_body, Icons.Outlined.PhotoLibrary) {
            navigation.onOpenPhotoCredits()
        }
        AboutRow(Res.string.profile_licences, Res.string.profile_licences_body, Icons.Outlined.Description) {
            navigation.onOpenLicences()
        }
    }
}

@Composable
private fun AboutRow(
    title: StringResource,
    body: StringResource,
    icon: ImageVector,
    onOpen: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
    )
}

@Composable
internal fun VersionRow() {
    val version = rememberAppVersion() ?: return
    ListItem(
        headlineContent = { Text(stringResource(Res.string.profile_version)) },
        supportingContent = { Text(version) },
    )
}

private fun Appearance.label(): StringResource =
    when (this) {
        Appearance.SYSTEM -> Res.string.appearance_system
        Appearance.LIGHT -> Res.string.appearance_light
        Appearance.DARK -> Res.string.appearance_dark
    }
