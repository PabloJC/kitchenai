package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.agent.AgentOrchestrator
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import kotlinx.coroutines.flow.firstOrNull

/**
 * Asks for suggestions built from what is stored about this user and this kitchen right now.
 *
 * [userId] and [kitchenId] are not interchangeable: the profile (dietary preferences, household)
 * stays personal per #190/#191, while the pantry it is matched against is the kitchen's shared
 * one.
 *
 * The profile is read with `firstOrNull`: a listener that has failed ends its stream, and
 * `first` on an ended stream would throw across a layer boundary instead of failing.
 *
 * [languageTags] names the language to answer in and comes from the caller rather than the
 * profile: the stored value is captured once, on first launch, and never revisited, so reading
 * it here would answer in whatever locale that first launch happened to be in (#131).
 */
class SuggestRecipesUseCase(
    private val profiles: UserProfileRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val orchestrator: AgentOrchestrator,
    private val units: GetUnitConverterUseCase,
    private val taxonomies: TaxonomyRepositoryContract,
) {
    suspend operator fun invoke(
        userId: UserId,
        kitchenId: KitchenId,
        languageTags: List<String>,
        options: SuggestionOptions = SuggestionOptions(),
    ): AppResult<List<RecipeSuggestion>> {
        val profile =
            profiles.observeProfile(userId).firstOrNull()
                ?: return AppResult.Failure(AppError.NotFound("profile"))
        return when (val held = pantry.getPantry(kitchenId)) {
            is AppResult.Failure -> held
            is AppResult.Success ->
                when (val converter = units()) {
                    is AppResult.Failure -> converter
                    is AppResult.Success ->
                        when (val catalogue = taxonomies.getTaxonomies()) {
                            is AppResult.Failure -> catalogue
                            is AppResult.Success ->
                                orchestrator.suggest(
                                    profile.withoutStructuralTerms(catalogue.data),
                                    held.data,
                                    options,
                                    languageTags,
                                    converter.data,
                                )
                        }
                }
        }
    }

    /**
     * A term from a taxonomy that declares a purpose is not a taste, whatever an older build let
     * the user tap: it never reaches the agent as a constraint or a preference.
     */
    private fun UserProfile.withoutStructuralTerms(catalogue: List<Taxonomy>): UserProfile {
        val structural = catalogue.filter { it.purpose != null }.map { it.id }.toSet()
        return copy(
            constraints = constraints.filterNot { it.term.taxonomy in structural },
            preferences = preferences.filterNot { it.taxonomy in structural },
        )
    }
}
