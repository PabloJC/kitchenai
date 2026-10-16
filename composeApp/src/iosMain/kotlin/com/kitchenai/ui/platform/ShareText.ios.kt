package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * Presented from the top-most controller: one already showing (a dialog's host) would refuse a
 * second presentation from the root. On iPad it is a popover, anchored where the share button sits.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberShareText(): (String) -> Unit =
    remember {
        { text ->
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            topController()?.let { top ->
                val width = top.view.bounds.useContents { size.width }
                sheet.popoverPresentationController?.sourceView = top.view
                sheet.popoverPresentationController?.sourceRect =
                    CGRectMake(width - ANCHOR_FROM_TRAILING, ANCHOR_FROM_TOP, 1.0, 1.0)
                top.presentViewController(sheet, animated = true, completion = null)
            }
        }
    }

private fun topController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

// Points: the centre of the second 48 pt icon from the trailing edge, below a status bar, where share sits.
private const val ANCHOR_FROM_TRAILING = 76.0
private const val ANCHOR_FROM_TOP = 80.0
