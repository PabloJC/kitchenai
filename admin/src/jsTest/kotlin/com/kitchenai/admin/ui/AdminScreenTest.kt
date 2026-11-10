package com.kitchenai.admin.ui

import com.kitchenai.admin.auth.AdminSession
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminScreenTest {
    @Test
    fun `nothing that reads Firestore renders before the claim is known to be true`() {
        assertEquals(AdminScreen.Loading, screenFor(AdminSession.Loading))
        assertEquals(AdminScreen.SignIn, screenFor(AdminSession.SignedOut))
        assertEquals(AdminScreen.NotAdmin("a@example.com"), screenFor(AdminSession.NotAdmin("a@example.com")))
    }

    @Test
    fun `an admin gets the shell`() {
        assertEquals(AdminScreen.Shell("a@example.com"), screenFor(AdminSession.Admin("a@example.com")))
    }

    @Test
    fun `a hash picks its section and anything else falls back to the first`() {
        assertEquals("recipes", sectionFor("#/recipes").path)
        assertEquals("free-text", sectionFor("#/free-text/").path)
        assertEquals("ingredients", sectionFor("").path)
        assertEquals("ingredients", sectionFor("#/nowhere").path)
    }
}
