package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.AppleSignInButton
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.profile_sign_in_benefit
import com.kitchenai.ui.resources.profile_sign_in_google
import com.kitchenai.ui.resources.profile_sign_out
import com.kitchenai.ui.resources.profile_signed_in_fallback
import org.jetbrains.compose.resources.stringResource

/**
 * Signed out or anonymous: the sign-in buttons, Apple's first where [onAppleSignIn] exists (iOS, guideline 4.8).
 * Signed in: the name the provider returned (or a generic label, never a blank one) and a way to sign out.
 */
@Composable
fun AccountSection(
    hasAccount: Boolean,
    displayName: String?,
    isAuthenticating: Boolean,
    onSignIn: () -> Unit,
    onAppleSignIn: (() -> Unit)?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hasAccount) {
        SignedInRow(displayName, isAuthenticating, onSignOut, modifier)
    } else {
        // Says what signing in keeps: a guest deciding whether to sign in is deciding about their data.
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            onAppleSignIn?.let { onClick -> AppleSignInButton(onClick = onClick, enabled = !isAuthenticating) }
            Button(onClick = onSignIn, enabled = !isAuthenticating, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.profile_sign_in_google))
            }
            Text(
                text = stringResource(Res.string.profile_sign_in_benefit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignedInRow(
    displayName: String?,
    isAuthenticating: Boolean,
    onSignOut: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.large),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayName ?: stringResource(Res.string.profile_signed_in_fallback),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onSignOut, enabled = !isAuthenticating) {
            Text(stringResource(Res.string.profile_sign_out))
        }
    }
}
