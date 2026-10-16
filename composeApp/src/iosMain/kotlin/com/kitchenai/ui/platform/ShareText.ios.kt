package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * Presented from the top-most controller: one already showing (a dialog's host) would refuse a
 * second presentation from the root. On iPad it is a popover whose arrow points at [anchor].
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberShareText(): (text: String, anchor: Rect?) -> Unit {
    val density = LocalDensity.current.density
    return remember(density) {
        { text, anchor ->
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            val window = keyWindow()
            topController(window)?.let { top ->
                val popover = sheet.popoverPresentationController
                // The Compose view fills the window (ignoresSafeArea), so window pixels / density are window points.
                if (anchor != null && window != null) {
                    popover?.sourceView = window
                    popover?.sourceRect =
                        CGRectMake(
                            anchor.left / density.toDouble(),
                            anchor.top / density.toDouble(),
                            anchor.width / density.toDouble(),
                            anchor.height / density.toDouble(),
                        )
                } else {
                    popover?.sourceView = top.view
                    popover?.sourceRect =
                        top.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
                }
                top.presentViewController(sheet, animated = true, completion = null)
            }
        }
    }
}

// `UIApplication.keyWindow` is deprecated and ambiguous with several scenes; ask the foreground one.
private fun keyWindow(): UIWindow? =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?.keyWindow

private fun topController(window: UIWindow?): UIViewController? {
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
