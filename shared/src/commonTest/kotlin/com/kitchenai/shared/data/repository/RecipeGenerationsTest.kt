package com.kitchenai.shared.data.repository

import com.kitchenai.shared.data.local.RecipeEntity
import com.kitchenai.shared.data.local.toEntity
import com.kitchenai.shared.domain.usecase.recipe.recipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RecipeGenerationsTest {
    @Test
    fun `rows are grouped by their stamp newest first and keep their order within one`() {
        val rows =
            listOf(
                recipe(id = "old-1").toEntity(at(1_000)),
                recipe(id = "new-1").toEntity(at(2_000)),
                recipe(id = "old-2").toEntity(at(1_000)),
                recipe(id = "new-2").toEntity(at(2_000)),
            )

        val generations = rows.toGenerations().map { generation -> generation.map { it.id.value } }

        assertEquals(listOf(listOf("new-1", "new-2"), listOf("old-1", "old-2")), generations)
    }

    @Test
    fun `a generation whose rows no longer decode is left out`() {
        val broken = RecipeEntity(id = "broken", title = "t", source = "", savedAtMillis = 3_000, payload = "not json")
        val rows = listOf(broken, recipe(id = "kept").toEntity(at(1_000)))

        assertEquals(listOf(listOf("kept")), rows.toGenerations().map { generation -> generation.map { it.id.value } })
    }

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)
}
