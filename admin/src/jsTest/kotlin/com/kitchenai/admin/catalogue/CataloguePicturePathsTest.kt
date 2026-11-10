package com.kitchenai.admin.catalogue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CataloguePicturePathsTest {
    @Test
    fun `a picture is named after its ingredient and the upload time`() {
        assertEquals("catalogue/ingredients/egg-1700000000000.webp", ingredientPicturePath("egg", 1_700_000_000_000))
    }

    @Test
    fun `only a download URL under the ingredient folder is one this admin may delete`() {
        assertTrue(
            isIngredientPictureUrl(
                "https://storage.example/v0/b/bucket/o/catalogue%2Fingredients%2Fegg-1.webp?alt=media",
            ),
        )
        assertFalse(isIngredientPictureUrl("https://storage.example/v0/b/bucket/o/catalogue%2Frecipes%2Fstew.webp"))
        assertFalse(isIngredientPictureUrl("https://elsewhere.example/egg.webp"))
    }

    @Test
    fun `a large picture shrinks to the longest side and a small one is left alone`() {
        assertEquals(1024 to 512, scaledToFit(4000, 2000, 1024))
        assertEquals(384 to 1024, scaledToFit(1500, 4000, 1024))
        assertEquals(300 to 200, scaledToFit(300, 200, 1024))
    }
}
