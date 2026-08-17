package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher

/**
 * The last generation, matched against the pantry as it stands now rather than as it stood when
 * it was generated — a launch that finds the pantry changed must not show stale coverage.
 *
 * One pantry read for the whole list, not one per recipe: [PantryMatcher] is pure, so matching
 * every stored recipe against the same snapshot costs nothing further reads would not.
 */
class GetStoredSuggestionsUseCase(
    private val recipes: RecipeRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(userId: UserId): AppResult<List<RecipeSuggestion>> =
        recipes.getAll().flatMap { stored ->
            pantry.getPantry(userId).map { held ->
                val now = time.now()
                stored.map { recipe -> RecipeSuggestion(recipe, PantryMatcher.match(recipe, held, now), recipe.source) }
            }
        }
}
