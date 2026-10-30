package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract

/**
 * The name the kitchen shows for someone, chosen by them rather than whatever the sign-in
 * provider sent: a guest has none, and Apple sends one only on the first authorisation.
 *
 * Written to the profile and to the caller's own entry in their kitchen, which is what other
 * members read. Unlike [SaveUserProfileUseCase]'s best-effort mirror, a failing kitchen write is
 * reported: showing the name to the others is the whole point of setting it here.
 */
class SetDisplayNameUseCase(
    private val profiles: UserProfileRepositoryContract,
    private val kitchens: KitchenRepositoryContract,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        name: String,
        languageTags: List<String>,
    ): AppResult<Unit> {
        val trimmed = name.trim()
        if (trimmed.length !in 1..MAX_LENGTH) {
            return AppResult.Failure(AppError.Validation("displayName", "must be 1 to $MAX_LENGTH characters"))
        }
        val now = time.now()
        // A guest who never saved preferences has no profile document yet; this is its first write.
        val profile =
            when (val stored = profiles.getProfile(userId)) {
                is AppResult.Success -> stored.data
                is AppResult.Failure ->
                    if (stored.error is AppError.NotFound) {
                        UserProfile.newFor(
                            userId,
                            languageTags,
                            now,
                        )
                    } else {
                        return stored
                    }
            }
        val saved = profiles.save(profile.copy(displayName = trimmed, updatedAt = now))
        if (saved is AppResult.Failure) return saved
        return when (val kitchen = kitchens.getMyKitchen(userId)) {
            // No kitchen yet: the name is written into it when one is created or joined.
            is AppResult.Failure -> if (kitchen.error is AppError.NotFound) AppResult.Success(Unit) else kitchen
            is AppResult.Success -> kitchens.updateMyDisplayName(userId, kitchen.data.id, trimmed)
        }
    }

    companion object {
        /** Long enough for a full name, short enough for one row of the member list. */
        const val MAX_LENGTH = 40
    }
}
