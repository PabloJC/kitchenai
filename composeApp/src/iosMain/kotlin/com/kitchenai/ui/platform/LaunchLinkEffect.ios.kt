package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.kitchenai.ui.navigation.LaunchLinks
import com.kitchenai.ui.navigation.LaunchTarget
import org.koin.compose.koinInject

@Composable
actual fun LaunchLinkEffect(onTarget: (LaunchTarget) -> Unit) {
    val links = koinInject<LaunchLinks>()
    val open by rememberUpdatedState(onTarget)
    LaunchedEffect(links) {
        links.links.collect { link -> LaunchTarget.parse(link)?.let(open) }
    }
}
