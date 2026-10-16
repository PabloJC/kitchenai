package com.kitchenai.ui.platform

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext

/**
 * Always through the chooser: a default share target picked once would otherwise skip it for good.
 * The chooser is a bottom sheet, so the anchor goes unused.
 */
@Composable
actual fun rememberShareText(): (text: String, anchor: Rect?) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text, _ ->
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            val chooser = Intent.createChooser(send, null)
            // Only a non-Activity context needs a task of its own; from an Activity it would split the back stack.
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
