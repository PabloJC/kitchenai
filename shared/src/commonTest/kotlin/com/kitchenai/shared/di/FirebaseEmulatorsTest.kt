package com.kitchenai.shared.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirebaseEmulatorsTest {
    @Test
    fun `a blank host is production`() {
        assertNull(FirebaseEmulators.from("", 8080))
        assertNull(FirebaseEmulators.from("   ", 8080))
    }

    @Test
    fun `a host is trimmed and keeps the firebase json ports`() {
        val emulators = FirebaseEmulators.from(" 127.0.0.1 ", 8187)

        assertEquals(
            FirebaseEmulators(host = "127.0.0.1", firestorePort = 8187, authPort = 9099, functionsPort = 5001),
            emulators,
        )
    }

    @Test
    fun `a port that is not a port falls back to the default`() {
        assertEquals(8080, FirebaseEmulators.from("127.0.0.1", 0)?.firestorePort)
        assertEquals(8080, FirebaseEmulators.from("127.0.0.1", 70000)?.firestorePort)
    }
}
