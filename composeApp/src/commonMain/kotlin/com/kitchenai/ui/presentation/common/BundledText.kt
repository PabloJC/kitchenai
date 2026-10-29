package com.kitchenai.ui.presentation.common

import com.kitchenai.ui.resources.Res
import org.jetbrains.compose.resources.MissingResourceException

/** A file packaged under `composeResources/`, or null when the bundle lacks it: a screen shows that, not a crash. */
internal suspend fun readBundledText(path: String): String? =
    try {
        Res.readBytes(path).decodeToString()
    } catch (ignored: MissingResourceException) {
        null
    }
