package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase

/**
 * The kept generations, newest first, matched against the pantry as it stands now rather than as
 * it stood when they were generated — a launch that finds the pantry changed must not show stale
 * coverage.
 *
 * One pantry read for every list, not one per recipe: [PantryMatcher] is pure, so matching every
 * stored recipe against the same snapshot costs nothing further reads would not.
 */
class GetStoredSuggestionsUseCase(
    private val recipes: RecipeRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val time: TimeProvider,
    private val units: GetUnitConverterUseCase,
) {
    suspend operator fun invoke(kitchenId: KitchenId): AppResult<List<List<RecipeSuggestion>>> =
        recipes.getGenerations().flatMap { generations ->
            pantry.getPantry(kitchenId).flatMap { held ->
                units().map { converter ->
                    val now = time.now()
                    val zone = time.zone()
                    generations.map { generation ->
                        generation.map { recipe ->
                            RecipeSuggestion(
                                recipe,
                                PantryMatcher.match(recipe, held, now, converter, zone),
                                recipe.source,
                            )
                        }
                    }
                }
            }
        }
}
