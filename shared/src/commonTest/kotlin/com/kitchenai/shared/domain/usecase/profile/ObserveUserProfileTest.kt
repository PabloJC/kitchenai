package com.kitchenai.shared.domain.usecase.profile

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.HouseholdContext
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfilePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ObserveUserProfileTest {
    private val user = userId("u1")
    private val profile = UserProfile.newFor(user, listOf("xx"), Instant.fromEpochSeconds(1))

    @Test
    fun `emits every profile the port publishes in order`() =
        runTest {
            val updated = profile.copy(household = HouseholdContext(servings = 2))
            val useCase = ObserveUserProfile(FakeProfilePort(flowOf(profile, updated)))

            useCase(user).test {
                assertEquals(profile, awaitItem())
                assertEquals(updated, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a failing listener reports on errors and emits no profile`() =
        runTest {
            val error = AppError.NotFound("profile")
            val useCase = ObserveUserProfile(FakeProfilePort(emptyFlow(), error))

            useCase(user).test { awaitComplete() }
            useCase.errors(user).test {
                assertEquals(error, awaitItem())
                awaitComplete()
            }
        }
}

private class FakeProfilePort(
    private val stream: Flow<UserProfile>,
    private val failure: AppError? = null,
) : UserProfilePort {
    override fun observeProfile(userId: UserId): Flow<UserProfile> = stream

    override fun profileErrors(userId: UserId): Flow<AppError> = failure?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun save(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)
}

private fun userId(raw: String): UserId = (UserId.of(raw) as AppResult.Success).data
