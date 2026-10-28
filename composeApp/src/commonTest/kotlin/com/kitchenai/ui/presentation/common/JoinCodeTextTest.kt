package com.kitchenai.ui.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JoinCodeTextTest {
    @Test
    fun `a code is found inside a sentence and shown upper case`() {
        assertEquals(
            "0F8FAD5B-D9CB-469F-A165-70867728950E",
            extractJoinCode("Join my kitchen with code 0f8fad5b-d9cb-469f-a165-70867728950e!"),
        )
    }

    @Test
    fun `text that only looks partly like a code yields nothing`() {
        assertNull(extractJoinCode("0f8fad5b-d9cb-469f"))
        assertNull(extractJoinCode("hello"))
    }

    @Test
    fun `the code is displayed upper case`() {
        assertEquals("0F8FAD5B-D9CB", displayJoinCode("0f8fad5b-d9cb"))
    }
}
