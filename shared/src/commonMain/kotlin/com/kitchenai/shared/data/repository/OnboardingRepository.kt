package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.local.OnboardingLocalDataSource
import com.kitchenai.shared.data.local.localCall
import com.kitchenai.shared.domain.port.OnboardingRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Anything but the value this writes reads as not done: showing the flow again beats skipping it wrongly. */
class OnboardingRepository(
    private val local: OnboardingLocalDataSource,
    private val dispatchers: DispatcherProvider,
) : OnboardingRepositoryContract {
    override fun observeCompleted(): Flow<Boolean> = local.stored.map { it == COMPLETED }

    override suspend fun markCompleted(): AppResult<Unit> = localCall(dispatchers) { local.write(COMPLETED) }

    private companion object {
        const val COMPLETED = "true"
    }
}
