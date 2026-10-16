package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * Presented from the top-most controller: one already showing (a dialog's host) would refuse a
 * second presentation from the root. The popover anchor is what an iPad needs to show it at all.
 */
@Composable
actual fun rememberShareText(): (String) -> Unit =
    remember {
        { text ->
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            topController()?.let { top ->
                sheet.popoverPresentationController?.sourceView = top.view
                top.presentViewController(sheet, animated = true, completion = null)
            }
        }
    }

private fun topController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
