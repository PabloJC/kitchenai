package com.kitchenai.shared.data.local

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeLocalDataSourceTest {
    private val dataSource = RecipeLocalDataSource(FakeRecipeDao())

    @Test
    fun `an entity written on one launch is readable on the next`() =
        runTest {
            val entity = entity(id = "recipe-1")

            dataSource.replaceAll(listOf(entity))

            assertEquals(listOf(entity), dataSource.getAll())
        }

    @Test
    fun `a replaced generation leaves nothing behind`() =
        runTest {
            dataSource.replaceAll(listOf(entity(id = "recipe-1")))

            dataSource.replaceAll(listOf(entity(id = "recipe-2")))

            assertEquals(listOf(entity(id = "recipe-2")), dataSource.getAll())
        }

    private fun entity(id: String): RecipeEntity =
        RecipeEntity(id = id, title = "title-$id", source = "catalogue", savedAtMillis = 1_000, payload = "{}")
}
