package com.kitchenai.ui.platform

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.util.Consumer

@Composable
actual fun ReminderTapEffect(onOpen: () -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val open by rememberUpdatedState(onOpen)
    DisposableEffect(activity) {
        // The extra is removed once read, so a rotation does not open the pantry a second time.
        fun handle(intent: Intent) {
            if (intent.getBooleanExtra(ExpiryReminderReceiver.EXTRA_OPEN_PANTRY, false)) {
                intent.removeExtra(ExpiryReminderReceiver.EXTRA_OPEN_PANTRY)
                open()
            }
        }
        handle(activity.intent)
        val listener = Consumer<Intent> { intent -> handle(intent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}
