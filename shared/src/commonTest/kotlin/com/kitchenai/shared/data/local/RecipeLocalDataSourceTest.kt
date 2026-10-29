package com.kitchenai.shared.data.local

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeLocalDataSourceTest {
    private val dataSource = RecipeLocalDataSource(FakeRecipeDao())

    @Test
    fun `an entity written on one launch is readable on the next`() =
        runTest {
            val entity = entity(id = "recipe-1", storedAt = 1_000)

            dataSource.addGeneration(listOf(entity), keep = 3)

            assertEquals(listOf(entity), dataSource.getAll())
        }

    @Test
    fun `a new generation keeps the earlier one`() =
        runTest {
            dataSource.addGeneration(listOf(entity(id = "recipe-1", storedAt = 1_000)), keep = 3)

            dataSource.addGeneration(listOf(entity(id = "recipe-2", storedAt = 2_000)), keep = 3)

            assertEquals(listOf("recipe-1", "recipe-2"), dataSource.getAll().map { it.id })
        }

    @Test
    fun `generations past the limit are dropped whole and oldest first`() =
        runTest {
            dataSource.addGeneration(listOf(entity("a-1", 1_000), entity("a-2", 1_000)), keep = 2)
            dataSource.addGeneration(listOf(entity("b-1", 2_000)), keep = 2)

            dataSource.addGeneration(listOf(entity("c-1", 3_000), entity("c-2", 3_000)), keep = 2)

            assertEquals(listOf("b-1", "c-1", "c-2"), dataSource.getAll().map { it.id })
        }

    private fun entity(
        id: String,
        storedAt: Long,
    ): RecipeEntity =
        RecipeEntity(
            id = id,
            title = "title-$id",
            source = "catalogue",
            savedAtMillis = storedAt,
            payload = "{}",
        )
}
