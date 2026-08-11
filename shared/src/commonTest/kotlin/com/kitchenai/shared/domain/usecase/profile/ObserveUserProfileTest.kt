package com.kitchenai.shared.domain.usecase.profile

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.HouseholdContext
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfilePort
import kotlinx.coroutines.flow.Flow
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
            val useCase =
                ObserveUserProfile(
                    FakeProfilePort(flowOf(AppResult.Success(profile), AppResult.Success(updated))),
                )

            useCase(user).test {
                assertEquals(AppResult.Success(profile), awaitItem())
                assertEquals(AppResult.Success(updated), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a failure travels in the stream instead of ending it`() =
        runTest {
            val error = AppError.NotFound("profile")
            val useCase = ObserveUserProfile(FakeProfilePort(flowOf(AppResult.Failure(error))))

            useCase(user).test {
                assertEquals(AppResult.Failure(error), awaitItem())
                awaitComplete()
            }
        }
}

private class FakeProfilePort(
    private val stream: Flow<AppResult<UserProfile>>,
) : UserProfilePort {
    override fun observeProfile(userId: UserId): Flow<AppResult<UserProfile>> = stream

    override suspend fun save(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)
}

private fun userId(raw: String): UserId = (UserId.of(raw) as AppResult.Success).data
