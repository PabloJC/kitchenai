package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.layout.Arrangement
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
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.profile_sign_in_google
import com.kitchenai.ui.resources.profile_sign_out
import com.kitchenai.ui.resources.profile_signed_in_fallback
import org.jetbrains.compose.resources.stringResource

/**
 * Signed out or anonymous: a "Sign in with Google" affordance. Signed in with Google: the name
 * the platform returned (or a generic label, never a blank one) and a way to sign out.
 */
@Composable
fun AccountSection(
    signedInWithGoogle: Boolean,
    displayName: String?,
    isAuthenticating: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (signedInWithGoogle) {
        SignedInRow(displayName, isAuthenticating, onSignOut, modifier)
    } else {
        Button(
            onClick = onSignIn,
            enabled = !isAuthenticating,
            modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.large),
        ) {
            Text(stringResource(Res.string.profile_sign_in_google))
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
