package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.FakeKitchenRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.kitchen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SetDisplayNameUseCaseTest {
    private val now = Instant.fromEpochSeconds(500)
    private val userId = (UserId.of("u1") as AppResult.Success).data
    private val profiles = InMemoryProfiles()
    private val kitchens = FakeKitchenRepositoryContract(kitchen(id = "kitchen-1", ownerId = userId))
    private val setName = SetDisplayNameUseCase(profiles, kitchens, TimeProvider { now })

    @Test
    fun `the trimmed name is written to the profile and to their kitchen entry`() =
        runTest {
            profiles.stored = UserProfile.newFor(userId, listOf("xx"), Instant.fromEpochSeconds(1))

            val result = setName(userId, "  Sam  ", listOf("xx"))

            assertEquals(AppResult.Success(Unit), result)
            assertEquals("Sam", profiles.stored?.displayName)
            assertEquals(now, profiles.stored?.updatedAt)
            assertEquals("Sam", kitchens.updatedDisplayNames.single().third)
        }

    @Test
    fun `a guest with no profile yet gets one carrying the name`() =
        runTest {
            val result = setName(userId, "Sam", listOf("xx"))

            assertEquals(AppResult.Success(Unit), result)
            assertEquals("Sam", profiles.stored?.displayName)
            assertEquals(listOf("xx"), profiles.stored?.languageTags)
        }

    @Test
    fun `a blank or overlong name is refused and nothing is written`() =
        runTest {
            val blank = setName(userId, "   ", listOf("xx"))
            val long = setName(userId, "a".repeat(SetDisplayNameUseCase.MAX_LENGTH + 1), listOf("xx"))

            assertTrue((blank as AppResult.Failure).error is AppError.Validation)
            assertTrue((long as AppResult.Failure).error is AppError.Validation)
            assertNull(profiles.stored)
            assertTrue(kitchens.updatedDisplayNames.isEmpty())
        }

    @Test
    fun `no kitchen yet is not a failure`() =
        runTest {
            val alone = SetDisplayNameUseCase(profiles, FakeKitchenRepositoryContract(), TimeProvider { now })

            assertEquals(AppResult.Success(Unit), alone(userId, "Sam", listOf("xx")))
        }

    @Test
    fun `a failing kitchen write is reported since the others would not see the name`() =
        runTest {
            kitchens.updateMyDisplayNameResult = AppResult.Failure(AppError.Network())

            assertTrue(setName(userId, "Sam", listOf("xx")) is AppResult.Failure)
        }

    @Test
    fun `a failing profile read other than not found is reported before any write`() =
        runTest {
            profiles.readError = AppError.Network()

            assertTrue(setName(userId, "Sam", listOf("xx")) is AppResult.Failure)
            assertTrue(kitchens.updatedDisplayNames.isEmpty())
        }
}

private class InMemoryProfiles : UserProfileRepositoryContract {
    var stored: UserProfile? = null
    var readError: AppError? = null

    override fun observeProfile(userId: UserId): Flow<UserProfile> = emptyFlow()

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getProfile(userId: UserId): AppResult<UserProfile> =
        readError?.let { AppResult.Failure(it) }
            ?: stored?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound("profile"))

    override suspend fun save(profile: UserProfile): AppResult<Unit> {
        stored = profile
        return AppResult.Success(Unit)
    }
}
