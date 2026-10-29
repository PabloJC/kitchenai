package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.JoinKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.KitchenMembershipLock
import com.kitchenai.shared.domain.usecase.kitchen.LeaveKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RegenerateKitchenJoinCodeUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RemoveKitchenMemberUseCase
import com.kitchenai.shared.domain.usecase.kitchen.TransferKitchenOwnershipUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.kitchen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The owner handing the kitchen over, which is what lets them leave it (#351). */
@OptIn(ExperimentalCoroutinesApi::class)
class KitchenOwnershipTest {
    private val dispatcher = StandardTestDispatcher()
    private val self = userId("self")
    private val other = userId("other")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the owner offers each other member as owner but not their own row`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self, other)))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            val members = viewModel.state.value.kitchen!!.members
            assertTrue(members.single { it.id == other }.canMakeOwner)
            assertFalse(members.single { it.id == self }.canMakeOwner)
        }

    @Test
    fun `making someone else the owner asks first and then lets the old owner leave`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self, other)))
            val viewModel = viewModel(kitchens)
            val events = mutableListOf<KitchenEvent>()
            backgroundScope.launch { viewModel.events.collect { events += it } }
            viewModel.start(self)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.kitchen!!.canLeave)

            viewModel.requestMakeOwner(other)
            assertTrue(viewModel.state.value.confirming is KitchenConfirmation.MakeOwner)
            assertTrue(kitchens.transfers.isEmpty())

            viewModel.confirm()
            advanceUntilIdle()

            assertEquals(listOf(other), kitchens.transfers)
            assertTrue(events.single() is KitchenEvent.OwnerChanged)
            val after = viewModel.state.value.kitchen!!
            assertFalse(after.isOwner)
            assertTrue(after.canLeave)
            assertNull(after.leaveDisabledReason)
        }

    @Test
    fun `a refused hand-over is reported and the owner stays`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self, other)))
            kitchens.transferResult = AppResult.Failure(AppError.Network())
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.requestMakeOwner(other)
            viewModel.confirm()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.error is UiText)
            assertTrue(viewModel.state.value.kitchen!!.isOwner)
        }

    private fun viewModel(kitchens: FakeKitchenPort): KitchenViewModel {
        val lock = KitchenMembershipLock()
        val profile = UserProfile.newFor(self, listOf("aa"), Instant.fromEpochSeconds(0))
        return KitchenViewModel(
            observeKitchen = ObserveKitchenUseCase(kitchens),
            observeProfile = ObserveUserProfileUseCase(SingleProfile(profile)),
            writes =
                KitchenWritesDelegate(
                    join = JoinKitchenUseCase(kitchens, lock),
                    leave = LeaveKitchenUseCase(kitchens, lock),
                    removeMember = RemoveKitchenMemberUseCase(kitchens),
                    regenerateJoinCode = RegenerateKitchenJoinCodeUseCase(kitchens),
                    transferOwnership = TransferKitchenOwnershipUseCase(kitchens, lock),
                ),
        )
    }

    private fun userId(raw: String): UserId = (UserId.of(raw) as AppResult.Success).data
}

private class SingleProfile(private val profile: UserProfile) : UserProfileRepositoryContract {
    override fun observeProfile(userId: UserId): Flow<UserProfile> = flowOf(profile)

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getProfile(userId: UserId): AppResult<UserProfile> = AppResult.Success(profile)

    override suspend fun save(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)
}
