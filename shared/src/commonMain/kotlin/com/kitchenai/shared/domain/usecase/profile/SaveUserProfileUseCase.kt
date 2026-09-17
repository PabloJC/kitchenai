package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import kotlinx.coroutines.flow.firstOrNull

/**
 * Validates the profile against the live catalogue and stamps [UserProfile.updatedAt] before
 * writing. A term whose taxonomy is not in the catalogue is rejected rather than stored: it
 * would be unresolvable for every reader.
 *
 * A changed [UserProfile.displayName] also refreshes the caller's own entry in
 * [com.kitchenai.shared.domain.model.Kitchen.memberDisplayNames] — the one place profile data
 * and kitchen data intersect, and what lets a member list render without a cross-user profile
 * read (#190).
 */
class SaveUserProfileUseCase(
    private val profiles: UserProfileRepositoryContract,
    private val taxonomies: TaxonomyRepositoryContract,
    private val kitchens: KitchenRepositoryContract,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(profile: UserProfile): AppResult<Unit> {
        validate(profile)?.let { return AppResult.Failure(it) }
        // Read before writing: comparing against the profile about to be overwritten is the only
        // way to tell "the name changed" from "the household size changed".
        val previousDisplayName = profiles.observeProfile(profile.userId).firstOrNull()?.displayName
        val saved = profiles.save(profile.copy(updatedAt = time.now()))
        if (saved is AppResult.Failure) return saved
        val displayName = profile.displayName
        if (displayName == null || displayName == previousDisplayName) return saved
        return syncDisplayName(profile.userId, displayName)
    }

    /** No kitchen yet is not a failure: there is nothing to refresh until one exists. */
    private suspend fun syncDisplayName(
        userId: UserId,
        displayName: String,
    ): AppResult<Unit> =
        when (val kitchen = kitchens.getMyKitchen(userId)) {
            is AppResult.Failure -> if (kitchen.error is AppError.NotFound) AppResult.Success(Unit) else kitchen
            is AppResult.Success -> kitchens.updateMyDisplayName(userId, kitchen.data.id, displayName)
        }

    /** The catalogue read and the field checks share one exit, so [invoke] only branches on the result. */
    private suspend fun validate(profile: UserProfile): AppError? {
        val known =
            when (val catalogue = taxonomies.getTaxonomies()) {
                is AppResult.Failure -> return catalogue.error
                is AppResult.Success -> catalogue.data.map { it.id }.toSet()
            }
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
