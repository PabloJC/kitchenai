package com.kitchenai.shared.data.local

/** In memory, keyed by id like the real table. [RecipeDao.replaceAll] is the interface's own default, unfaked. */
internal class FakeRecipeDao : RecipeDao {
    private val rows = mutableMapOf<String, RecipeEntity>()

    override suspend fun getAll(): List<RecipeEntity> = rows.values.toList()

    override suspend fun insertAll(recipes: List<RecipeEntity>) {
        recipes.forEach { rows[it.id] = it }
    }

    override suspend fun clear() {
        rows.clear()
    }
}
