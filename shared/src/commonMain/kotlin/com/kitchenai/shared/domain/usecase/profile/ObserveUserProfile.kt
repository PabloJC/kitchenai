package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfilePort
import kotlinx.coroutines.flow.Flow

/** The profile stream as the UI consumes it. A failing listener reports on [errors]. */
class ObserveUserProfile(
    private val profiles: UserProfilePort,
) {
    operator fun invoke(userId: UserId): Flow<UserProfile> = profiles.observeProfile(userId)

    /** The listener's failures, collected alongside the stream above. */
    fun errors(userId: UserId): Flow<AppError> = profiles.profileErrors(userId)
}
