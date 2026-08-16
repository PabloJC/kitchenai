package com.kitchenai.shared.data.local

/**
 * The local half of the recipe data sources: a thin wrapper over [RecipeDao] in Room's own row
 * shape. It knows nothing about [com.kitchenai.shared.domain.model.Recipe] — that mapping, and
 * coordinating this with a remote data source if one ever exists, is
 * [com.kitchenai.shared.data.repository.RecipeRepository]'s job.
 */
class RecipeLocalDataSource(private val dao: RecipeDao) {
    suspend fun getAll(): List<RecipeEntity> = dao.getAll()

    suspend fun replaceAll(recipes: List<RecipeEntity>) = dao.replaceAll(recipes)
}
