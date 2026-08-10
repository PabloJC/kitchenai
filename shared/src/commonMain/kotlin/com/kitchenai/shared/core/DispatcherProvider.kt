package com.kitchenai.shared.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Los dispatchers se inyectan, nunca se referencian directamente desde
 * casos de uso o repositorios: así los tests pueden sustituirlos.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = ioDispatcher()
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}

/** `Dispatchers.IO` sólo existe en JVM/Native; en common se resuelve por plataforma. */
internal expect fun ioDispatcher(): CoroutineDispatcher
