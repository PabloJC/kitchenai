package com.kitchenai.shared.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One call per key at a time: a caller arriving while [run] is in flight for the same key awaits
 * that call's answer. A finished key is forgotten, so a later caller runs again.
 */
class SingleFlight<K, V> {
    private val lock = Mutex()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun run(
        key: K,
        block: suspend () -> V,
    ): V {
        while (true) {
            val (call, owner) =
                lock.withLock {
                    inFlight[key]?.let { it to false }
                        ?: (CompletableDeferred<V>().also { inFlight[key] = it } to true)
                }
            if (!owner) {
                call.join()
                // A cancelled owner leaves no answer: this caller takes the key over and runs it.
                if (!call.isCancelled) return call.await()
                continue
            }
            try {
                return block().also { call.complete(it) }
            } finally {
                forget(key)
                // No-op after a completion; otherwise wakes the waiters to take over.
                call.cancel()
            }
        }
    }

    private suspend fun forget(key: K) = withContext(NonCancellable) { lock.withLock { inFlight.remove(key) } }
}
