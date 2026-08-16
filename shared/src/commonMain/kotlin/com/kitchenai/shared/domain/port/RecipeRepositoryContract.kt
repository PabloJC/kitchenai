package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe

/**
 * The local recipe store: what needs to survive leaving the app without belonging in Firestore
 * (#52 already rejected that for a generation nobody may open).
 *
 * [replaceAll] is the only write. Its first caller is a generation that supersedes the one
 * before it wholesale, not a row that changes on its own — that is also why there is no
 * single-recipe write here yet.
 *
 * This is deliberately not the same seam as [RecipePort], which serves the read-only catalogue
 * and a user's saved recipes over Firestore: #139 unifies the two into one contract backed by a
 * local and a remote data source. Until then, two `Recipe`-shaped ports is the honest state of
 * the code, not an oversight.
 */
interface RecipeRepositoryContract {
    suspend fun getAll(): AppResult<List<Recipe>>

    suspend fun replaceAll(recipes: List<Recipe>): AppResult<Unit>
}
