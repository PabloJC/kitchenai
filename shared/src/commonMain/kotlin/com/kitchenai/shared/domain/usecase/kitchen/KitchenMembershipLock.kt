package com.kitchenai.shared.domain.usecase.kitchen

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises every use case that reads the caller's kitchen and then writes their membership, so
 * the read cannot go stale in between: a provisioning that saw "none" while a join landed would
 * leave the user in two kitchens. Not reentrant: a holder must not call another use case that takes it.
 */
class KitchenMembershipLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
