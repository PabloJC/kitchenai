package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

@Composable
actual fun rememberNotificationPermissionRequest(): (onResult: (granted: Boolean) -> Unit) -> Unit =
    remember {
        { onResult ->
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            ) { granted, _ -> onResult(granted) }
        }
    }
