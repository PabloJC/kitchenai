package com.kitchenai.ui.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Links handed in from outside Compose: iOS quick actions arrive through the scene delegate,
 * possibly before the shell has composed. Conflated, so the last one waits for the shell.
 */
class LaunchLinks {
    private val pending = Channel<String>(Channel.CONFLATED)
    val links: Flow<String> = pending.receiveAsFlow()

    fun open(link: String) {
        pending.trySend(link)
    }
}
