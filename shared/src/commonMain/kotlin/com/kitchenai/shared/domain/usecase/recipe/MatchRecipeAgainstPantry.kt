package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.scaledTo
import com.kitchenai.shared.domain.port.PantryPort
import com.kitchenai.shared.domain.port.RecipePort
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher

/**
 * Answers "can I cook this tonight" from stored facts only.
 *
 * Both reads are one-shot: a match computed from the first emission of a listener would hang
 * for good once that listener had failed.
 *
 * [servings] re-scales the recipe before matching. Without it a screen whose stepper says four
 * would keep answering "you have everything" from the amounts for two — quantities on screen
 * saying one thing and the buckets beside them saying another.
 */
class MatchRecipeAgainstPantry(
    private val recipes: RecipePort,
    private val pantry: PantryPort,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        recipeId: RecipeId,
        servings: Int? = null,
    ): AppResult<PantryMatch> =
        when (val recipe = recipes.getRecipe(recipeId)) {
            is AppResult.Failure -> recipe
            // A scaling failure propagates untouched: an impossible serving count is the
            // caller's error, and answering it with a match would hide that.
            is AppResult.Success ->
                recipe.data.at(servings).flatMap { scaled ->
                    pantry.getPantry(userId).map { held -> PantryMatcher.match(scaled, held, time.now()) }
                }
        }

    private fun Recipe.at(servings: Int?): AppResult<Recipe> =
        if (servings == null) AppResult.Success(this) else scaledTo(servings)
}
