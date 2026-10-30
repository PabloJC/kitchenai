package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.SetDisplayNameUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayNameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val self = (UserId.of("self") as AppResult.Success).data
    private val profiles = MutableProfiles()
    private val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self)))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        DisplayNameViewModel(
            ObserveUserProfileUseCase(profiles),
            SetDisplayNameUseCase(profiles, kitchens, TimeProvider { Instant.fromEpochSeconds(0) }),
        ).also { it.start(self, listOf("aa")) }

    @Test
    fun `a guest names themself and the kitchen shows it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            assertNull(viewModel.state.value.name)

            viewModel.edit()
            viewModel.save("  Sam ")
            advanceUntilIdle()

            assertEquals("Sam", viewModel.state.value.name)
            assertFalse(viewModel.state.value.isEditing)
            assertEquals("Sam", (kitchens.getMyKitchen(self) as AppResult.Success).data.memberDisplayNames[self.value])
        }

    @Test
    fun `a refused name keeps the dialog open with the reason`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.edit()
            viewModel.save("   ")
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isEditing)
            assertNotNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isSaving)
        }

    @Test
    fun `cancelling closes the dialog and clears a refusal`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.edit()
            viewModel.save("")
            advanceUntilIdle()

            viewModel.cancel()

            assertFalse(viewModel.state.value.isEditing)
            assertNull(viewModel.state.value.error)
        }
}

private class MutableProfiles : UserProfileRepositoryContract {
    private val stored = MutableStateFlow<UserProfile?>(null)

    override fun observeProfile(userId: UserId): Flow<UserProfile> = stored.filterNotNull()

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getProfile(userId: UserId): AppResult<UserProfile> =
        stored.value?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound("profile"))

    override suspend fun save(profile: UserProfile): AppResult<Unit> {
        stored.value = profile
        return AppResult.Success(Unit)
    }
}
