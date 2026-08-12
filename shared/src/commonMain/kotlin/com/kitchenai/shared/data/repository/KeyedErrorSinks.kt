package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * One error stream per listener key. A single stream per repository would report one listener's
 * failure to every other observer that repository has open.
 */
internal class KeyedErrorSinks<K> {
    private val sinks = MutableStateFlow<Map<K, MutableSharedFlow<AppError>>>(emptyMap())

    fun of(key: K): MutableSharedFlow<AppError> =
        sinks
            .updateAndGet { open -> if (key in open) open else open + (key to errorSink()) }
            .getValue(key)
}

/** Buffered so that publishing a failure never suspends the listener that is dying. */
internal fun errorSink(): MutableSharedFlow<AppError> = MutableSharedFlow(extraBufferCapacity = 1)
