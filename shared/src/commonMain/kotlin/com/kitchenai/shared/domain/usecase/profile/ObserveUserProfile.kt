package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfilePort
import kotlinx.coroutines.flow.Flow

/** The profile stream as the UI consumes it. Failures travel in the stream, not as throws. */
class ObserveUserProfile(
    private val profiles: UserProfilePort,
) {
    operator fun invoke(userId: UserId): Flow<AppResult<UserProfile>> = profiles.observeProfile(userId)
}
