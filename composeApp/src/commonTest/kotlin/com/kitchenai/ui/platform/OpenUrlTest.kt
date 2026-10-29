package com.kitchenai.ui.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenUrlTest {
    @Test
    fun `only an https address reaches the browser`() {
        assertTrue(isWebLink("https://commons.wikimedia.org/wiki/File:Naan_Bread.JPG"))
        assertFalse(isWebLink("http://example.com"))
        assertFalse(isWebLink("kitchenai://join/abc"))
        assertFalse(isWebLink("file:///etc/hosts"))
        assertFalse(isWebLink("javascript:alert(1)"))
    }

    @Test
    fun `a blank or bare scheme is no address at all`() {
        assertFalse(isWebLink(""))
        assertFalse(isWebLink("https://"))
    }
}
