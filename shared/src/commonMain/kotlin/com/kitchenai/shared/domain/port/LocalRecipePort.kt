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
 */
interface LocalRecipePort {
    suspend fun getAll(): AppResult<List<Recipe>>

    suspend fun replaceAll(recipes: List<Recipe>): AppResult<Unit>
}
