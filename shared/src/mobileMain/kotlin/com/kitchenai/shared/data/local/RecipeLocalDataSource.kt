package com.kitchenai.shared.data.local

/**
 * The local half of the recipe data sources: a thin wrapper over [RecipeDao] in Room's own row
 * shape. It knows nothing about [com.kitchenai.shared.domain.model.Recipe] — that mapping, and
 * coordinating this with a remote data source if one ever exists, is
 * [com.kitchenai.shared.data.repository.RecipeRepository]'s job.
 */
class RecipeLocalDataSource(private val dao: RecipeDao) {
    suspend fun getAll(): List<RecipeEntity> = dao.getAll()

    suspend fun getById(id: String): RecipeEntity? = dao.getById(id)

    /** Replaces one row in place; the caller keeps its stamp, so it stays in its generation. */
    suspend fun replace(recipe: RecipeEntity) = dao.insertAll(listOf(recipe))

    suspend fun addGeneration(
        recipes: List<RecipeEntity>,
        keep: Int,
    ) = dao.addGeneration(recipes, keep)
}
