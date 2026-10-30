package com.kitchenai.shared.data.local

/** In memory, keyed by id like the real table. [RecipeDao.addGeneration] is the interface's own default, unfaked. */
internal class FakeRecipeDao : RecipeDao {
    private val rows = mutableMapOf<String, RecipeEntity>()

    override suspend fun getAll(): List<RecipeEntity> = rows.values.toList()

    override suspend fun insertAll(recipes: List<RecipeEntity>) {
        // REPLACE deletes the old row first, so a replaced recipe moves to the end like a new one.
        recipes.forEach {
            rows.remove(it.id)
            rows[it.id] = it
        }
    }

    override suspend fun deleteStoredBefore(cutoffMillis: Long) {
        rows.values.removeAll { it.savedAtMillis < cutoffMillis }
    }
}
