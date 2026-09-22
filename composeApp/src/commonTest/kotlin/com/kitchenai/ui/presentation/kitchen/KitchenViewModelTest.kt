package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.kitchen.JoinKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.LeaveKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RegenerateKitchenJoinCodeUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RemoveKitchenMemberUseCase
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.kitchen
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_action
import com.kitchenai.ui.resources.kitchen_invalid_code
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KitchenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val self = userId("self")
    private val other = userId("other")

    // `viewModelScope` runs on Dispatchers.Main, absent outside an app.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is a kitchen until the listener emits one`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self)))
            val viewModel = viewModel(kitchens)

            assertTrue(viewModel.state.value.isLoading)
            viewModel.start(self)
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.isLoading)
            assertEquals(self, viewModel.state.value.kitchen?.members?.single()?.id)
        }

    @Test
    fun `joining writes the trimmed code and clears the draft once it succeeds`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = other, memberIds = setOf(other, self)))
            kitchens.joinResult = AppResult.Success(kitchen(ownerId = other, joinCode = "new-code"))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.onJoinCodeInputChange("  new-code  ")
            viewModel.join()
            advanceUntilIdle()

            assertEquals(listOf(joinCode("new-code")), kitchens.joinCalls)
            assertEquals("", viewModel.state.value.joinCodeInput)
            assertNull(viewModel.state.value.error)
            assertEquals("new-code", viewModel.state.value.kitchen?.joinCode)
        }

    @Test
    fun `a code matching no kitchen is reported without touching the draft`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self)))
            kitchens.joinResult = AppResult.Failure(AppError.NotFound("kitchenInvite"))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.onJoinCodeInputChange("does-not-exist")
            viewModel.join()
            advanceUntilIdle()

            assertEquals(UiText.of(Res.string.kitchen_invalid_code), viewModel.state.value.error)
            assertEquals("does-not-exist", viewModel.state.value.joinCodeInput)
        }

    @Test
    fun `leaving as a plain member succeeds`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = other, memberIds = setOf(other, self)))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.leave()
            advanceUntilIdle()

            assertEquals(1, kitchens.leaveCount)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `leaving is rejected for an owner with other members and never reaches the port`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self, other)))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.leave()
            advanceUntilIdle()

            assertEquals(0, kitchens.leaveCount)
            assertTrue(viewModel.state.value.error != null)
            // The UI state already carried this before the tap: canLeave mirrors the same rule.
            assertEquals(false, viewModel.state.value.kitchen?.canLeave)
        }

    @Test
    fun `the owner can remove another member`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self, other)))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.removeMember(other)
            advanceUntilIdle()

            assertEquals(listOf(other), kitchens.removedMembers)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `a non-owner cannot remove a member and never reaches the port`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = other, memberIds = setOf(other, self)))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.removeMember(other)
            advanceUntilIdle()

            assertEquals(emptyList(), kitchens.removedMembers)
            assertEquals(UiText.of(Res.string.error_unauthorized_action), viewModel.state.value.error)
        }

    @Test
    fun `the owner can regenerate the join code and the new one reaches the screen`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self)))
            kitchens.regenerateResult = AppResult.Success(kitchen(ownerId = self, joinCode = "fresh-code"))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.regenerateJoinCode()
            advanceUntilIdle()

            assertEquals(1, kitchens.regenerateCount)
            assertNull(viewModel.state.value.error)
            assertEquals("fresh-code", viewModel.state.value.kitchen?.joinCode)
        }

    @Test
    fun `a non-owner cannot regenerate the join code and never reaches the port`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = other, memberIds = setOf(other, self)))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.regenerateJoinCode()
            advanceUntilIdle()

            assertEquals(0, kitchens.regenerateCount)
            assertEquals(UiText.of(Res.string.error_unauthorized_action), viewModel.state.value.error)
        }

    @Test
    fun `a second tap while the first write is in flight writes once`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort(initial = kitchen(ownerId = self, memberIds = setOf(self)))
            kitchens.regenerateResult = AppResult.Success(kitchen(ownerId = self, joinCode = "fresh-code"))
            val viewModel = viewModel(kitchens)
            viewModel.start(self)
            advanceUntilIdle()

            viewModel.regenerateJoinCode()
            viewModel.regenerateJoinCode()
            advanceUntilIdle()

            assertEquals(1, kitchens.regenerateCount)
        }

    private fun viewModel(kitchens: FakeKitchenPort): KitchenViewModel {
        val leave = LeaveKitchenUseCase(kitchens)
        return KitchenViewModel(
            observeKitchen = ObserveKitchenUseCase(kitchens),
            writes =
                KitchenWritesDelegate(
                    join = JoinKitchenUseCase(kitchens, leave),
                    leave = leave,
                    removeMember = RemoveKitchenMemberUseCase(kitchens),
                    regenerateJoinCode = RegenerateKitchenJoinCodeUseCase(kitchens),
                ),
        )
    }

    private fun userId(raw: String): UserId = (UserId.of(raw) as AppResult.Success).data

    private fun joinCode(raw: String): KitchenJoinCode = (KitchenJoinCode.of(raw) as AppResult.Success).data
}
