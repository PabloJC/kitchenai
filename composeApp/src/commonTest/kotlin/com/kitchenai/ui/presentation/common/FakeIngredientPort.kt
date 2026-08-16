package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The catalogue as three screens see it. One copy, and it **can fail**: the version this
 * replaced in the pantry test returned `emptyFlow()` for its errors, so no test written against
 * it could ever have caught a screen that ignored a broken catalogue.
 *
 * Nothing is emitted until a test asks, because a real listener sends nothing until it has data.
 */
class FakeIngredientPort : IngredientRepositoryContract {
    private val stream = MutableSharedFlow<List<Ingredient>>(replay = 1)
    val errors = MutableSharedFlow<AppError>()

    suspend fun emit(ingredients: List<Ingredient>) {
        stream.emit(ingredients)
    }

    override fun observeIngredients(): Flow<List<Ingredient>> = stream

    override fun ingredientErrors(): Flow<AppError> = errors

    override suspend fun getIngredient(id: IngredientId): AppResult<Ingredient> =
        AppResult.Failure(AppError.NotFound("ingredient"))
}
