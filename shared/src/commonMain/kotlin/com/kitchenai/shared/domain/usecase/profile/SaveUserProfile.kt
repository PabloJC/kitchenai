package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.TaxonomyPort
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfilePort
import kotlinx.coroutines.flow.first

/**
 * Validates the profile against the live catalogue and stamps [UserProfile.updatedAt] before
 * writing. A term whose taxonomy is not in the catalogue is rejected rather than stored: it
 * would be unresolvable for every reader.
 */
class SaveUserProfile(
    private val profiles: UserProfilePort,
    private val taxonomies: TaxonomyPort,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(profile: UserProfile): AppResult<Unit> {
        val known =
            when (val catalogue = taxonomies.observeTaxonomies().first()) {
                is AppResult.Failure -> return AppResult.Failure(catalogue.error)
                is AppResult.Success -> catalogue.data.map { it.id }.toSet()
            }
        validate(profile, known)?.let { return AppResult.Failure(it) }
        return profiles.save(profile.copy(updatedAt = time.now()))
    }

    private fun validate(
        profile: UserProfile,
        known: Set<TaxonomyId>,
    ): AppError.Validation? {
        val terms = profile.constraints.map { it.term }
        return when {
            profile.household.servings < 1 ->
                AppError.Validation("household.servings", "must be at least 1")
            terms.distinct().size != terms.size ->
                AppError.Validation("constraints", "must not hold the same term twice")
            terms.any { it.taxonomy !in known } ->
                AppError.Validation("constraints", "references an unknown taxonomy")
            profile.preferences.any { it.taxonomy !in known } ->
                AppError.Validation("preferences", "references an unknown taxonomy")
            else -> null
        }
    }
}
