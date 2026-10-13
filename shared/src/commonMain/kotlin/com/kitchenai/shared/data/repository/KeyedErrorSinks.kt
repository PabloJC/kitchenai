package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import kotlinx.coroutines.channels.BufferOverflow
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

/**
 * Publishing never suspends: a listener must not wait on the slowest observer, or a stale error
 * is delivered after the recovery it preceded. Drop-oldest, since a repeated error adds nothing.
 */
internal fun errorSink(): MutableSharedFlow<AppError> =
    MutableSharedFlow(extraBufferCapacity = ERROR_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

private const val ERROR_BUFFER = 16
