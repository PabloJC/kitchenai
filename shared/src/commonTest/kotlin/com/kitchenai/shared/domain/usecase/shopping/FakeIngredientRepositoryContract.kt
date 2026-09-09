package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** In-memory catalogue; an id absent from [catalogue] answers not-found, same as a real miss. */
class FakeIngredientRepositoryContract(
    private val catalogue: List<Ingredient> = emptyList(),
) : IngredientRepositoryContract {
    override fun observeIngredients(): Flow<List<Ingredient>> = emptyFlow()

    override fun ingredientErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getIngredient(id: IngredientId): AppResult<Ingredient> =
        catalogue.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound("Ingredient"))
}
