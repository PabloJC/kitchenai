package com.kitchenai.ui.navigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchTargetTest {
    @Test
    fun `each shortcut link opens its own target`() {
        assertEquals(LaunchTarget.PANTRY, LaunchTarget.parse("kitchenai://pantry"))
        assertEquals(LaunchTarget.IDEAS, LaunchTarget.parse("kitchenai://ideas"))
        assertEquals(LaunchTarget.SHOPPING_ADD, LaunchTarget.parse("kitchenai://shopping/add"))
    }

    @Test
    fun `a trailing slash is the same link`() {
        assertEquals(LaunchTarget.PANTRY, LaunchTarget.parse("kitchenai://pantry/"))
    }

    @Test
    fun `a link that is not ours or no link at all opens the app as usual`() {
        assertNull(LaunchTarget.parse(null))
        assertNull(LaunchTarget.parse("https://example.com/pantry"))
        assertNull(LaunchTarget.parse("kitchenai://settings"))
    }

    @Test
    fun `an add request made before the screen listens waits for it`() =
        runTest {
            val requests = ShoppingAddRequests()

            requests.request()
            requests.request()

            assertEquals(Unit, requests.requests.first())
        }
}
