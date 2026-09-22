package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** The seam towards wherever the profile is stored; `domain` never learns it is Firestore. */
interface UserProfileRepositoryContract {
    fun observeProfile(userId: UserId): Flow<UserProfile>

    /** Failures of the listener above, which stops emitting rather than throwing. */
    fun profileErrors(userId: UserId): Flow<AppError>

    /**
     * One-shot read for read-modify-write callers: [observeProfile] never emits and never
     * completes for a uid with no profile document yet (it reports [AppError.NotFound] on
     * [profileErrors] instead), so `observeProfile(userId).firstOrNull()` hangs forever on
     * exactly the first-ever save — the same trap decision #68 (`docs/mvp-backlog.md`) already
     * closed for pantry/shopping list.
     */
    suspend fun getProfile(userId: UserId): AppResult<UserProfile>

    suspend fun save(profile: UserProfile): AppResult<Unit>
}
