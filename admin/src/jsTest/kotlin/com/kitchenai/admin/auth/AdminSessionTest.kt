package com.kitchenai.admin.auth

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminSessionTest {
    @Test
    fun `only an admin claim set to true is an admin`() {
        assertTrue(isAdminClaims(mapOf("admin" to true)))
        assertFalse(isAdminClaims(mapOf("admin" to "true")))
        assertFalse(isAdminClaims(mapOf("admin" to 1)))
        assertFalse(isAdminClaims(emptyMap()))
    }

    @Test
    fun `an account with the claim gets the admin session`() {
        assertEquals(AdminSession.Admin("a@example.com"), sessionFor("a@example.com", mapOf("admin" to true)))
    }

    @Test
    fun `an account without the claim gets the not-admin session`() {
        assertEquals(AdminSession.NotAdmin(null), sessionFor(null, mapOf("admin" to "true")))
    }

    @Test
    fun `claims that cannot be read sign the account out`() =
        runTest {
            assertEquals(AdminSession.SignedOut, sessionFor("a@example.com") { error("token revoked") })
        }

    @Test
    fun `claims read later still decide the session`() =
        runTest {
            assertEquals(AdminSession.Admin("a@example.com"), sessionFor("a@example.com") { mapOf("admin" to true) })
        }

    @Test
    fun `a cancelled claims read is not taken for a sign-out`() =
        runTest {
            assertFailsWith<CancellationException> { sessionFor(null) { throw CancellationException("stop") } }
        }
}
