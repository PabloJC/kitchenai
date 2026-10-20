package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.flow.Flow

/** Whether this device has been through the first-run flow; another device signing in sees it afresh. */
interface OnboardingRepositoryContract {
    /** Emits at once: false until [markCompleted] has succeeded on this device. */
    fun observeCompleted(): Flow<Boolean>

    suspend fun markCompleted(): AppResult<Unit>
}
