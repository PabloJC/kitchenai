package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController

/** Safari's own sheet rather than the Safari app, so the user stays in KitchenAI. */
@Composable
actual fun rememberOpenUrl(): (url: String) -> Unit =
    remember {
        { url ->
            val link = if (isWebLink(url)) NSURL.URLWithString(url) else null
            if (link != null) {
                topController(keyWindow())?.presentViewController(
                    SFSafariViewController(uRL = link),
                    animated = true,
                    completion = null,
                )
            }
        }
    }
