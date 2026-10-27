package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/**
 * Asks the system for permission to notify and reports the answer. Granted at once where no
 * runtime permission exists (Android before 13); the system itself never asks twice after a no.
 */
@Composable
expect fun rememberNotificationPermissionRequest(): (onResult: (granted: Boolean) -> Unit) -> Unit
