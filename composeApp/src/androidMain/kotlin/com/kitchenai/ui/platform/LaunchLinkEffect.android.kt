package com.kitchenai.ui.platform

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.util.Consumer
import com.kitchenai.ui.navigation.LaunchTarget

@Composable
actual fun LaunchLinkEffect(onTarget: (LaunchTarget) -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val open by rememberUpdatedState(onTarget)
    DisposableEffect(activity) {
        // The link is cleared once read, so a rotation does not follow it a second time.
        fun handle(intent: Intent) {
            val target = LaunchTarget.parse(intent.dataString) ?: return
            intent.data = null
            open(target)
        }
        handle(activity.intent)
        val listener = Consumer<Intent> { intent -> handle(intent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}
