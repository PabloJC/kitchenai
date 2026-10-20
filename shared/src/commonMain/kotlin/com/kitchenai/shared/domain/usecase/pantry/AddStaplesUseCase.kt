package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider

/**
 * Adds a one-tap set of catalogue ingredients, each as a plain count of one: nobody weighs their
 * rice on a first-run screen. One already held is left alone rather than topped up, so tapping a
 * staple the user already counted cannot change their number. Answers how many were added.
 */
class AddStaplesUseCase(
    private val pantry: PantryRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        ingredients: List<IngredientId>,
    ): AppResult<Int> {
        if (ingredients.isEmpty()) return AppResult.Success(0)
        return pantry.getPantry(kitchenId).flatMap { held ->
            val missing = ingredients.distinct() - held.mapNotNull { it.ingredient }.toSet()
            draft(missing).flatMap { drafted ->
                if (drafted.isEmpty()) {
                    AppResult.Success(0)
                } else {
                    pantry.upsertAll(kitchenId, drafted).map { drafted.size }
                }
            }
        }
    }

    /** All or nothing: one id the generator cannot supply fails the whole set, so nothing is half-added. */
    private fun draft(ingredients: List<IngredientId>): AppResult<List<PantryItem>> {
        val now = time.now()
        val drafted = mutableListOf<PantryItem>()
        for (ingredient in ingredients) {
            val item =
                PantryItemId.of(ids.newId()).flatMap { id ->
                    PantryItem.create(id = id, quantity = Quantity(1.0), updatedAt = now, ingredient = ingredient)
                }
            when (item) {
                is AppResult.Failure -> return item
                is AppResult.Success -> drafted += item.data
            }
        }
        return AppResult.Success(drafted)
    }
}
