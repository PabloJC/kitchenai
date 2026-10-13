package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyedErrorSinksTest {
    @Test
    fun `publishing never waits for a subscriber that has stopped collecting`() =
        runTest {
            val sink = errorSink()
            val stalled = launch { sink.collect { awaitCancellation() } }
            advanceUntilIdle()

            // More than any small buffer holds: a suspending sink would never let the last one through.
            val published =
                async(start = CoroutineStart.UNDISPATCHED) { repeat(50) { sink.emit(AppError.NotFound("kitchen")) } }
            advanceUntilIdle()

            assertTrue(published.isCompleted, "the publisher was left waiting on the stalled subscriber")
            stalled.cancel()
        }

    @Test
    fun `a fast subscriber still receives every error while another one is stalled`() =
        runTest {
            val sink = errorSink()
            val received = mutableListOf<AppError>()
            val stalled = launch { sink.collect { awaitCancellation() } }
            val fast = launch { sink.collect { received += it } }
            advanceUntilIdle()

            repeat(3) { sink.emit(AppError.NotFound("kitchen")) }
            advanceUntilIdle()

            assertEquals(3, received.size)
            stalled.cancel()
            fast.cancel()
        }

    @Test
    fun `one key's sink is not another key's`() =
        runTest {
            val sinks = KeyedErrorSinks<String>()

            assertTrue(sinks.of("a") !== sinks.of("b"))
            assertTrue(sinks.of("a") === sinks.of("a"))
        }
}
