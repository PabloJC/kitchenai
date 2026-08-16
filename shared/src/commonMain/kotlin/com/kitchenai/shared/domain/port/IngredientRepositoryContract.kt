package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import kotlinx.coroutines.flow.Flow

/** The catalogue seam. Read-only for the client: ingredients are shared data, not user data. */
interface IngredientRepositoryContract {
    fun observeIngredients(): Flow<List<Ingredient>>

    /** Failures of the listener above, which stops emitting rather than throwing. */
    fun ingredientErrors(): Flow<AppError>

    suspend fun getIngredient(id: IngredientId): AppResult<Ingredient>
}
