package com.kitchenai.admin.auth

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
