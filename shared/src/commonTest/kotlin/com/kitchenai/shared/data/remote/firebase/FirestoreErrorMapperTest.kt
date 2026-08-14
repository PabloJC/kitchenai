package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FirestoreErrorMapperTest {
    private val cause = IllegalStateException("boom")

    @Test
    fun `maps PERMISSION_DENIED to Unauthorized`() {
        assertEquals(AppError.Unauthorized(cause), appErrorForCode("PERMISSION_DENIED", cause))
    }

    @Test
    fun `maps UNAUTHENTICATED to Unauthorized`() {
        assertEquals(AppError.Unauthorized(cause), appErrorForCode("UNAUTHENTICATED", cause))
    }

    @Test
    fun `maps UNAVAILABLE to Network`() {
        assertEquals(AppError.Network(cause), appErrorForCode("UNAVAILABLE", cause))
    }

    @Test
    fun `maps DEADLINE_EXCEEDED to Timeout rather than to Network`() {
        // A call that timed out reached the network, so it must not come back as one that did not.
        assertEquals(AppError.Timeout(cause), appErrorForCode("DEADLINE_EXCEEDED", cause))
    }

    @Test
    fun `maps NOT_FOUND to NotFound`() {
        assertEquals(AppError.NotFound(FIRESTORE_RESOURCE), appErrorForCode("NOT_FOUND", cause))
    }

    @Test
    fun `maps every remaining Firestore code to Unknown keeping the cause`() {
        // The full code list of the SDK minus the five mapped above.
        val remaining =
            listOf(
                "OK",
                "CANCELLED",
                "UNKNOWN",
                "INVALID_ARGUMENT",
                "ALREADY_EXISTS",
                "RESOURCE_EXHAUSTED",
                "FAILED_PRECONDITION",
                "ABORTED",
                "OUT_OF_RANGE",
                "UNIMPLEMENTED",
                "INTERNAL",
                "DATA_LOSS",
            )

        remaining.forEach { code ->
            assertEquals(AppError.Unknown(cause), appErrorForCode(code, cause), "unexpected mapping for $code")
        }
    }

    @Test
    fun `maps a throwable that is not a Firestore failure to Unknown`() {
        val error = cause.toAppError()

        assertTrue(error is AppError.Unknown)
        assertSame(cause, error.cause)
    }

    @Test
    fun `rethrows CancellationException instead of mapping it`() {
        val cancellation = CancellationException("collector gone")

        val thrown = assertFailsWith<CancellationException> { cancellation.toAppError() }

        assertSame(cancellation, thrown)
    }
}
