package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** The seam towards wherever the profile is stored; `domain` never learns it is Firestore. */
interface UserProfilePort {
    fun observeProfile(userId: UserId): Flow<AppResult<UserProfile>>

    suspend fun save(profile: UserProfile): AppResult<Unit>
}
