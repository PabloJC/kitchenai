package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.port.IngredientPort
import kotlinx.coroutines.flow.Flow

/**
 * The catalogue stream, used by the pickers and to resolve identifiers into words. Ordering
 * is left to the caller: it depends on the displayed language, which the domain does not read.
 */
class ObserveIngredients(
    private val catalogue: IngredientPort,
) {
    operator fun invoke(): Flow<List<Ingredient>> = catalogue.observeIngredients()

    /** The listener's failures, collected alongside the stream above. */
    fun errors(): Flow<AppError> = catalogue.ingredientErrors()
}
