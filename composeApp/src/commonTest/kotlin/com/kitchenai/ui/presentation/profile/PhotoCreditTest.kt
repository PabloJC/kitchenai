package com.kitchenai.ui.presentation.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// That every dish_*.jpg has a credit, and that NOTICE.md agrees, is tools/__tests__/photo-credits.test.mjs.
class PhotoCreditTest {
    @Test
    fun `a credit with a licence deed and one without both parse`() {
        val json =
            """
            [
              {"image": "dish_soup", "title": "Tomato soup", "author": "JIP", "licence": "CC BY-SA 4.0",
               "licenceUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
               "source": "https://commons.wikimedia.org/a"},
              {"image": "dish_bbq", "title": "Smoked ribs", "author": "Jon Sullivan", "licence": "Public domain",
               "source": "https://commons.wikimedia.org/b", "addedLater": true}
            ]
            """.trimIndent()

        val credits = parsePhotoCredits(json)

        assertEquals(listOf("dish_soup", "dish_bbq"), credits?.map { it.image })
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", credits?.first()?.licenceUrl)
        assertNull(credits?.last()?.licenceUrl)
    }

    @Test
    fun `a credit missing its author is malformed rather than shown blank`() {
        val json = """[{"image": "dish_soup", "title": "Tomato soup", "licence": "CC0", "source": "https://a"}]"""

        assertNull(parsePhotoCredits(json))
    }

    @Test
    fun `text that is not JSON is malformed`() {
        assertNull(parsePhotoCredits("not json"))
    }
}
