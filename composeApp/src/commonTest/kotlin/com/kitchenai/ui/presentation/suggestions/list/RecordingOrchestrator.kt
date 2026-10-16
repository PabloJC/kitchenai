package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.agent.AgentOrchestrator
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.service.UnitConverter
import kotlinx.coroutines.CompletableDeferred

/**
 * The real use case over fake ports, so the wiring it does is exercised too. The orchestrator
 * is what counts calls, because the number of times a model is asked is the assertion this
 * screen exists to make. [gate], when set, is awaited before answering — how a stored set is
 * shown to still be up while a run has not landed yet is proven.
 */
class RecordingOrchestrator : AgentOrchestrator {
    var answer: AppResult<List<RecipeSuggestion>> = AppResult.Success(emptyList())
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0
        private set
    var lastOptions: SuggestionOptions? = null
        private set
    var lastLanguageTags: List<String>? = null
        private set

    override suspend fun suggest(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        languageTags: List<String>,
        units: UnitConverter,
    ): AppResult<List<RecipeSuggestion>> {
        calls++
        lastOptions = options
        lastLanguageTags = languageTags
        gate?.await()
        return answer
    }
}
