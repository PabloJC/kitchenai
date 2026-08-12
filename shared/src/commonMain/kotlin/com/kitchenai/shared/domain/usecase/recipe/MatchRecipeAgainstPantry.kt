package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryPort
import com.kitchenai.shared.domain.port.RecipePort
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher

/**
 * Answers "can I cook this tonight" from stored facts only.
 *
 * Both reads are one-shot: a match computed from the first emission of a listener would hang
 * for good once that listener had failed.
 */
class MatchRecipeAgainstPantry(
    private val recipes: RecipePort,
    private val pantry: PantryPort,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        recipeId: RecipeId,
    ): AppResult<PantryMatch> =
        when (val recipe = recipes.getRecipe(recipeId)) {
            is AppResult.Failure -> recipe
            is AppResult.Success ->
                pantry.getPantry(userId).map { held -> PantryMatcher.match(recipe.data, held, time.now()) }
        }
}
