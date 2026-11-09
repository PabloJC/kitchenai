package com.kitchenai.shared.data.system

import com.kitchenai.shared.core.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NonFatalSummaryTest {
    // What a user typed, as an SDK message or an error field could carry it.
    private val userContent = "Ana's tortilla with 3 eggs, ana@example.com, Calle Mayor 1"

    @Test
    fun `carries the operation and the error kind and nothing else`() {
        val summary = nonFatalSummary("suggestRecipes", AppError.Unknown(IllegalStateException(userContent)))

        assertEquals("suggestRecipes: Unknown", summary)
    }

    @Test
    fun `never carries user content from any error`() {
        val cause = IllegalArgumentException(userContent, RuntimeException(userContent))
        val errors =
            listOf(
                AppError.Timeout(cause),
                AppError.Unauthorized(cause),
                AppError.NotFound(userContent),
                AppError.Validation(userContent, userContent),
                AppError.Conflict(cause),
                AppError.Unknown(cause),
            )

        errors.forEach { error ->
            val summary = nonFatalSummary("operation", error).orEmpty()
            assertFalse("Ana" in summary || "@" in summary || "eggs" in summary, "leaked by $error: $summary")
        }
    }

    @Test
    fun `reports nothing for an offline user`() {
        assertNull(nonFatalSummary("operation", AppError.Network(IllegalStateException(userContent))))
    }

    @Test
    fun `reports nothing for a meal already served today`() {
        assertNull(nonFatalSummary("suggestRecipes", AppError.DailyLimit))
    }
}
