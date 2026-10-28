package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

@Composable
actual fun ReminderTapEffect(onOpen: () -> Unit) {
    val delegate = remember { ReminderTapDelegate() }
    DisposableEffect(delegate) {
        // The centre holds its delegate weakly; remember keeps this one alive for the app's life.
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.delegate = delegate
        onDispose { if (center.delegate == delegate) center.delegate = null }
    }
    val taps by delegate.taps.collectAsState()
    LaunchedEffect(taps) { if (taps > 0) onOpen() }
}

/** Counts taps on an expiry reminder, and lets a cooking timer ring with the app in front. */
private class ReminderTapDelegate :
    NSObject(),
    UNUserNotificationCenterDelegateProtocol {
    private val tapped = MutableStateFlow(0)
    val taps: StateFlow<Int> = tapped

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val id = didReceiveNotificationResponse.notification.request.identifier
        // update, not +=: the centre calls on a background thread, and two quick taps must both count.
        if (id.startsWith(REMINDER_ID_PREFIX)) tapped.update { it + 1 }
        withCompletionHandler()
    }

    // Without this iOS keeps a notification silent while the app is open, which a timer must not be.
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        val id = willPresentNotification.request.identifier
        val ringing = UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound
        withCompletionHandler(if (id.startsWith(COOKING_ALARM_ID_PREFIX)) ringing else 0uL)
    }
}
