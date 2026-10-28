package com.kitchenai.ui.platform

import android.content.Context

/** The white launcher glyph, looked up by name because its resource lives in :androidApp. */
internal fun notificationSmallIcon(context: Context): Int =
    context.resources
        .getIdentifier("ic_launcher_foreground", "drawable", context.packageName)
        .takeIf { it != 0 } ?: context.applicationInfo.icon
