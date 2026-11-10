package com.kitchenai.admin.core

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatchingFailuresTest {
    @Test
    fun `a value comes back as a success`() =
        runTest {
            assertEquals(Result.success(1), catchingFailures { 1 })
        }

    @Test
    fun `a failure comes back as a failure`() =
        runTest {
            assertTrue(catchingFailures { error("offline") }.isFailure)
        }

    @Test
    fun `a cancellation is rethrown`() =
        runTest {
            assertFailsWith<CancellationException> { catchingFailures { throw CancellationException("stop") } }
        }
}
