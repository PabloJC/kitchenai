package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.service.UnitConverter

/**
 * Runs one suggestion round: build the request, pick who answers it, verify what comes back.
 *
 * It lives in `domain` because that sequence is the product, not the vendor's; an agent is
 * replaceable, this is not. [units] is what coverage is verified with, so a pantry in kilos
 * covers a recipe in grams here exactly as it does everywhere else.
 */
interface AgentOrchestrator {
    suspend fun suggest(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        languageTags: List<String>,
        units: UnitConverter = UnitConverter.NONE,
    ): AppResult<List<RecipeSuggestion>>
}
