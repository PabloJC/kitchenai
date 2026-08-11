package com.kitchenai.shared.data.remote.firebase

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirestoreCallTest {
    @Test
    fun `returns Success with the value the block produced`() =
        runTest {
            val result = firestoreCall(testDispatchers(StandardTestDispatcher(testScheduler))) { "pantry" }

            assertEquals(AppResult.Success("pantry"), result)
        }

    @Test
    fun `returns Failure instead of letting the exception escape`() =
        runTest {
            val cause = IllegalStateException("offline")

            val result = firestoreCall<String>(testDispatchers(StandardTestDispatcher(testScheduler))) { throw cause }

            assertTrue(result is AppResult.Failure)
            assertEquals(AppError.Unknown(cause), result.error)
        }

    @Test
    fun `rethrows CancellationException so the caller stays cancellable`() =
        runTest {
            val cancellation = CancellationException("caller gone")

            val thrown =
                assertFailsWith<CancellationException> {
                    firestoreCall<String>(testDispatchers(StandardTestDispatcher(testScheduler))) { throw cancellation }
                }

            // Not `assertSame`: coroutines recreates the exception to recover the stack trace.
            assertEquals(cancellation.message, thrown.message)
        }

    @Test
    fun `runs the block on the injected io dispatcher`() =
        runTest {
            val io = StandardTestDispatcher(testScheduler)
            var ran = false
            val job = launch(start = CoroutineStart.UNDISPATCHED) { firestoreCall(testDispatchers(io)) { ran = true } }

            // UNDISPATCHED runs inline up to the first suspension: the block is still queued,
            // which is only true if the call switched to the injected dispatcher.
            assertFalse(ran)

            job.join()
            assertTrue(ran)
        }

    @Test
    fun `a snapshot flow passes its emissions through untouched`() =
        runTest {
            val errors = MutableSharedFlow<AppError>()

            flowOf(1, 2).reportingErrorsTo(errors).test {
                assertEquals(1, awaitItem())
                assertEquals(2, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a listener error ends the stream and is published on the error flow`() =
        runTest {
            val cause = IllegalStateException("listener died")
            val errors = MutableSharedFlow<AppError>(replay = 1)

            flow<Int> {
                emit(1)
                throw cause
            }.reportingErrorsTo(errors).test {
                assertEquals(1, awaitItem())
                awaitComplete()
            }

            assertEquals(AppError.Unknown(cause), errors.replayCache.single())
        }

    private fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }
}
