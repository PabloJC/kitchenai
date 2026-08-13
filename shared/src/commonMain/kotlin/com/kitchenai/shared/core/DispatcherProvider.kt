package com.kitchenai.shared.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers are injected, never referenced directly from use cases or repositories, so
 * tests can replace them.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

// The implementation of the port every other file has to inject: naming a dispatcher here is the point.
@Suppress("InjectDispatcher")
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = ioDispatcher()
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}

/** `Dispatchers.IO` only exists on JVM/Native; in common it is resolved per platform. */
internal expect fun ioDispatcher(): CoroutineDispatcher
