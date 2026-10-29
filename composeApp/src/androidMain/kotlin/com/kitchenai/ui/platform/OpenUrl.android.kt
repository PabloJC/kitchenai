package com.kitchenai.ui.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

@Composable
actual fun rememberOpenUrl(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            if (isWebLink(url)) {
                val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
                if (context !is Activity) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    tab.launchUrl(context, url.toUri())
                } catch (ignored: ActivityNotFoundException) {
                    // A device with no browser at all: there is nowhere to open the page, and nothing to recover.
                }
            }
        }
    }
}
