package com.kitchenai.ui.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * "Put the cursor in the add field", from a shortcut to the Shopping screen. Conflated, so a
 * request made before the screen has composed waits for it rather than being lost.
 */
class ShoppingAddRequests {
    private val pending = Channel<Unit>(Channel.CONFLATED)
    val requests: Flow<Unit> = pending.receiveAsFlow()

    fun request() {
        pending.trySend(Unit)
    }
}
