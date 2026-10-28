package com.kitchenai.ui.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * "Open Ideas on what expires soon", from Pantry's Use soon section. Conflated, so a request made
 * before Ideas has composed waits for it; the filter is set, nothing is generated yet.
 */
class IdeasRequests {
    private val pending = Channel<Unit>(Channel.CONFLATED)
    val useExpiring: Flow<Unit> = pending.receiveAsFlow()

    fun requestUseExpiring() {
        pending.trySend(Unit)
    }
}
