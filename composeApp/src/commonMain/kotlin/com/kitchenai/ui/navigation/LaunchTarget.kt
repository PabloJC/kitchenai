package com.kitchenai.ui.navigation

/**
 * Where a launch from outside the app lands: an app shortcut, later a widget. Links are this
 * app's own `kitchenai://` addresses; anything else is not ours and opens the app as usual.
 */
enum class LaunchTarget(
    internal val link: String,
) {
    PANTRY("kitchenai://pantry"),
    IDEAS("kitchenai://ideas"),
    SHOPPING_ADD("kitchenai://shopping/add"),
    ;

    companion object {
        fun parse(link: String?): LaunchTarget? = entries.firstOrNull { it.link == link?.trimEnd('/') }
    }
}
