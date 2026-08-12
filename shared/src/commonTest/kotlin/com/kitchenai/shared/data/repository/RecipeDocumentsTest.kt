package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.mapper.recipeDocument
import com.kitchenai.shared.data.remote.dto.RecipeDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two rules that run before Firestore is involved: what may be written, and in which order a
 * recipe is looked for.
 */
class RecipeDocumentsTest {
    @Test
    fun `accepts a recipe of exactly the size limit`() {
        val document = recipeDocumentOf(MAX_SAVED_RECIPE_BYTES)

        assertEquals(MAX_SAVED_RECIPE_BYTES, document.serialisedBytes())
        assertEquals(AppResult.Success(Unit), document.withinDocumentLimit())
    }

    @Test
    fun `rejects a recipe one byte past the size limit`() {
        val document = recipeDocumentOf(MAX_SAVED_RECIPE_BYTES + 1)

        assertEquals(AppResult.Failure(AppError.Validation("recipe", "too large")), document.withinDocumentLimit())
    }

    @Test
    fun `takes the saved copy and never reads the catalogue`() =
        runTest {
            val reached = mutableListOf<Int>()

            val found = firstFound(RESOURCE, reads(reached, saved = "recipe-1", catalogued = "recipe-2"))

            assertEquals(AppResult.Success("recipe-1"), found)
            assertEquals(listOf(SAVED), reached)
        }

    @Test
    fun `falls through to the catalogue when the user saved nothing`() =
        runTest {
            val reached = mutableListOf<Int>()

            val found = firstFound(RESOURCE, reads(reached, saved = null, catalogued = "recipe-2"))

            assertEquals(AppResult.Success("recipe-2"), found)
            assertEquals(listOf(SAVED, CATALOGUE), reached)
        }

    @Test
    fun `reports not found when neither place holds the recipe`() =
        runTest {
            val found = firstFound(RESOURCE, reads(mutableListOf(), saved = null, catalogued = null))

            assertEquals(AppResult.Failure(AppError.NotFound(RESOURCE)), found)
        }

    @Test
    fun `stops at the first read that fails rather than reading on`() =
        runTest {
            val reached = mutableListOf<Int>()
            val failing: suspend () -> AppResult<String?> = {
                reached += SAVED
                AppResult.Failure(AppError.Unauthorized())
            }

            val found = firstFound(RESOURCE, listOf(failing) + reads(reached, null, "recipe-2").drop(1))

            assertEquals(AppResult.Failure(AppError.Unauthorized()), found)
            assertEquals(listOf(SAVED), reached)
        }

    /** The two reads of [firstFound], each recording that it ran so the order can be asserted. */
    private fun reads(
        reached: MutableList<Int>,
        saved: String?,
        catalogued: String?,
    ): List<suspend () -> AppResult<String?>> =
        listOf(
            {
                reached += SAVED
                AppResult.Success(saved)
            },
            {
                reached += CATALOGUE
                AppResult.Success(catalogued)
            },
        )

    /**
     * A document of exactly [bytes] once serialised. The padding is a repeated single-byte
     * character, so every character added to the summary adds one byte to the payload.
     */
    private fun recipeDocumentOf(bytes: Int): RecipeDto {
        val empty = recipeDocument(summary = "")
        return recipeDocument(summary = "-".repeat(bytes - empty.serialisedBytes()))
    }

    private companion object {
        const val RESOURCE = "recipe"
        const val SAVED = 1
        const val CATALOGUE = 2
    }
}
