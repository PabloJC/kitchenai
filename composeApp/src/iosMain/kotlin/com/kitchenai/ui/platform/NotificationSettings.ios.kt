package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString

@Composable
actual fun rememberOpenNotificationSettings(): () -> Unit =
    remember {
        {
            NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString)?.let { url ->
                UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
            }
        }
    }
