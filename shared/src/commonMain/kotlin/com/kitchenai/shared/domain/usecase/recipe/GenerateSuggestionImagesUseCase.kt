package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Recipe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** The server runs few image instances; more calls at once from one device only queue there. */
private const val AT_ONCE = 2

/**
 * Gives each of [recipes] that has a key and no photo its own, in list order and [AT_ONCE] at a
 * time. Each landed recipe reaches [onPictured]; a failure skips that recipe and the rest carry on.
 */
class GenerateSuggestionImagesUseCase(
    private val generateImage: GenerateRecipeImageUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId?,
        recipes: List<Recipe>,
        onPictured: (Recipe) -> Unit,
    ) {
        val permits = Semaphore(AT_ONCE)
        coroutineScope {
            recipes
                .filter { it.imageUrl == null && it.imageKey != null }
                .forEach { recipe ->
                    launch {
                        permits.withPermit {
                            val result = generateImage(kitchenId, recipe)
                            if (result is AppResult.Success) onPictured(result.data)
                        }
                    }
                }
        }
    }
}
