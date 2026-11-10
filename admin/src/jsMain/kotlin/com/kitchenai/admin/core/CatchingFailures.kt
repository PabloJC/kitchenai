package com.kitchenai.admin.core

import kotlin.coroutines.cancellation.CancellationException

/** [runCatching] that lets a cancellation through: catching it would keep a cancelled coroutine running. */
suspend fun <T> catchingFailures(block: suspend () -> T): Result<T> =
    runCatching { block() }.onFailure { failure -> if (failure is CancellationException) throw failure }
