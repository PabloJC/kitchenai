package com.kitchenai.ui.platform

import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

// `UIApplication.keyWindow` is deprecated and ambiguous with several scenes; ask the foreground one.
internal fun keyWindow(): UIWindow? =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?.keyWindow

/** The top-most controller: one already presenting (a dialog's host) refuses a second presentation. */
internal fun topController(window: UIWindow?): UIViewController? {
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
