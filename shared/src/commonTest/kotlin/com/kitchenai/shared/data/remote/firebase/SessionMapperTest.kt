package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionMapperTest {
    @Test
    fun `a null uid maps to signed out`() {
        assertEquals(Session.SignedOut, sessionOf(uid = null, isAnonymous = true))
    }

    @Test
    fun `a blank uid maps to signed out rather than to an unusable identifier`() {
        assertEquals(Session.SignedOut, sessionOf(uid = "   ", isAnonymous = true))
    }

    @Test
    fun `an anonymous user maps to a signed in session carrying its uid`() {
        val session = assertIs<Session.SignedIn>(sessionOf(uid = "uid-1", isAnonymous = true))

        assertEquals("uid-1", session.userId.value)
        assertEquals(true, session.isAnonymous)
    }

    @Test
    fun `a permanent user keeps the anonymous flag down`() {
        val session = assertIs<Session.SignedIn>(sessionOf(uid = "uid-2", isAnonymous = false))

        assertEquals("uid-2", session.userId.value)
        assertEquals(false, session.isAnonymous)
    }
}
