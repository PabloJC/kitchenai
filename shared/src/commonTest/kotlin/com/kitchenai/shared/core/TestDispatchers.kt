package com.kitchenai.shared.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Every dispatcher pointed at one the test controls. Extracted on its second use rather than
 * its third, which is how the repository ended up with four helpers written three times.
 */
internal fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
    object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
