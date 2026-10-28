package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/** Opens this app's own notification settings, where a refused permission can be allowed again. */
@Composable
expect fun rememberOpenNotificationSettings(): () -> Unit
