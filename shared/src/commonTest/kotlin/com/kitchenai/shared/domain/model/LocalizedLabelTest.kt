package com.kitchenai.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Language tags and labels are placeholders on purpose: a real one would be a contextual
// constant, and the resolution rules do not depend on which languages exist.
class LocalizedLabelTest {
    private val labels = mapOf("xx" to "one", "yy-YY" to "two", "zz" to "three")

    @Test
    fun `an exact tag wins`() {
        assertEquals("two", labels.resolve(listOf("yy-YY")))
    }

    @Test
    fun `a regional tag falls back to the primary subtag`() {
        assertEquals("one", labels.resolve(listOf("xx-QQ")))
    }

    @Test
    fun `the first preference wins even when it only matches by primary subtag`() {
        assertEquals("one", labels.resolve(listOf("xx-QQ", "zz")))
    }

    @Test
    fun `tag matching ignores case`() {
        assertEquals("two", labels.resolve(listOf("YY-yy")))
    }

    @Test
    fun `the declared default answers when no preference matches`() {
        assertEquals("three", labels.resolve(listOf("qq"), defaultLanguageTag = "zz"))
    }

    @Test
    fun `a miss returns null rather than a placeholder`() {
        assertNull(labels.resolve(listOf("qq"), defaultLanguageTag = "rr"))
        assertNull(emptyMap<String, String>().resolve(listOf("xx")))
    }
}
