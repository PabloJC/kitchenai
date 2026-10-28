package com.kitchenai.ui

import com.kitchenai.ui.navigation.LaunchLinks
import org.koin.mp.KoinPlatform

/** Called from Swift with a quick action's type, which is one of the app's own kitchenai:// links. */
@Suppress("unused") // Swift calls it
fun openLaunchLink(link: String) {
    KoinPlatform.getKoin().get<LaunchLinks>().open(link)
}
