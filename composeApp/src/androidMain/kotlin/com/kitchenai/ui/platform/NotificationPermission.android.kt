package com.kitchenai.ui.platform

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
actual fun rememberNotificationPermissionRequest(): (onResult: (granted: Boolean) -> Unit) -> Unit {
    var waiting by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            waiting?.invoke(granted)
            waiting = null
        }
    return { onResult ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(true)
        } else {
            waiting = onResult
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
