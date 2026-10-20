package com.kitchenai.shared.domain.usecase.onboarding

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.port.OnboardingRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingUseCasesTest {
    private val repository = FakeOnboardingRepository()

    @Test
    fun `a fresh device has not been through the first run`() =
        runTest {
            assertFalse(ObserveOnboardingCompletedUseCase(repository)().first())
        }

    @Test
    fun `completing it is seen by observers`() =
        runTest {
            CompleteOnboardingUseCase(repository)()

            assertTrue(ObserveOnboardingCompletedUseCase(repository)().first())
        }
}

private class FakeOnboardingRepository : OnboardingRepositoryContract {
    private val completed = MutableStateFlow(false)

    override fun observeCompleted(): Flow<Boolean> = completed

    override suspend fun markCompleted(): AppResult<Unit> {
        completed.value = true
        return AppResult.Success(Unit)
    }
}
