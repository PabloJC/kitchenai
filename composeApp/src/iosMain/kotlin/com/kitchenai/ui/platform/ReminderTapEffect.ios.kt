package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/** Counts taps on an expiry reminder; any other notification is passed over. */
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
        if (id.startsWith(REMINDER_PREFIX)) tapped.value += 1
        withCompletionHandler()
    }

    private companion object {
        const val REMINDER_PREFIX = "expiry-reminder-"
    }
}
