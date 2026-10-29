package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/**
 * Opens a web page over the app, in Custom Tabs on Android and Safari's sheet on iOS, so the
 * user comes back to where they were. Anything but an https link is ignored.
 */
@Composable
expect fun rememberOpenUrl(): (url: String) -> Unit

/** Bundled and configured links only, but a scheme like `kitchenai://` or `file:` must never reach the browser. */
internal fun isWebLink(url: String): Boolean = url.startsWith("https://") && url.length > "https://".length
