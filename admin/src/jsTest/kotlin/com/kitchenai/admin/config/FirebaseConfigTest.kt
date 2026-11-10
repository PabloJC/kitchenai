package com.kitchenai.admin.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseConfigTest {
    private val complete =
        """{"apiKey":"key","authDomain":"domain","projectId":"project","storageBucket":"bucket","appId":"app"}"""

    @Test
    fun `reads Hosting's init json and ignores the keys it does not use`() {
        val config = parseFirebaseConfig(complete.dropLast(1) + ""","messagingSenderId":"sender"}""").getOrThrow()

        assertEquals("project", config.projectId)
        assertEquals(DEFAULT_FUNCTIONS_REGION, config.functionsRegion)
        assertFalse(config.useEmulators)
    }

    @Test
    fun `the local file can turn the emulators on`() {
        val config = parseFirebaseConfig(complete.dropLast(1) + ""","useEmulators":true}""").getOrThrow()

        assertTrue(config.useEmulators)
    }

    @Test
    fun `names every missing field`() {
        val failure = parseFirebaseConfig("""{"apiKey":" ","projectId":"project"}""").exceptionOrNull()

        assertEquals("The Firebase config has no apiKey, authDomain, storageBucket, appId", failure?.message)
    }

    @Test
    fun `refuses text that is not JSON`() {
        assertTrue(parseFirebaseConfig("<html>").isFailure)
    }

    @Test
    fun `only a local host reads the local file`() {
        assertEquals("firebase-config.local.json", configUrl("localhost"))
        assertEquals("firebase-config.local.json", configUrl("127.0.0.1"))
        assertEquals("/__/firebase/init.json", configUrl("admin.example.app"))
    }
}
