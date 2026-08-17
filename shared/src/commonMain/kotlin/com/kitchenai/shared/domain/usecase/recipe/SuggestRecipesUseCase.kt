package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.agent.AgentOrchestrator
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import kotlinx.coroutines.flow.firstOrNull

/**
 * Asks for suggestions built from what is stored about this user right now.
 *
 * The profile is read with `firstOrNull`: a listener that has failed ends its stream, and
 * `first` on an ended stream would throw across a layer boundary instead of failing.
 */
class SuggestRecipesUseCase(
    private val profiles: UserProfileRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val orchestrator: AgentOrchestrator,
) {
    suspend operator fun invoke(
        userId: UserId,
        options: SuggestionOptions = SuggestionOptions(),
    ): AppResult<List<RecipeSuggestion>> {
        val profile =
            profiles.observeProfile(userId).firstOrNull()
                ?: return AppResult.Failure(AppError.NotFound("profile"))
        return when (val held = pantry.getPantry(userId)) {
            is AppResult.Failure -> held
            is AppResult.Success -> orchestrator.suggest(profile, held.data, options)
        }
    }
}
