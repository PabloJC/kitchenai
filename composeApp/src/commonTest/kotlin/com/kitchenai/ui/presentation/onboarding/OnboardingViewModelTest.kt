package com.kitchenai.ui.presentation.onboarding

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.OnboardingRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.onboarding.CompleteOnboardingUseCase
import com.kitchenai.shared.domain.usecase.onboarding.ObserveOnboardingCompletedUseCase
import com.kitchenai.shared.domain.usecase.pantry.AddStaplesUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = (UserId.of("user-1") as AppResult.Success).data
    private val onboarding = FakeOnboardingPort()
    private val catalogue = FakeIngredientPort()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a device that has been through it goes straight to the app`() =
        runTest(dispatcher) {
            onboarding.completed.value = true

            val viewModel = started()
            advanceUntilIdle()

            assertEquals(OnboardingStatus.DONE, viewModel.state.value.status)
        }

    @Test
    fun `a new device with an empty pantry shows the first page`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            assertEquals(OnboardingStatus.SHOWING, viewModel.state.value.status)
            assertEquals(OnboardingStep.VALUE, viewModel.state.value.step)
        }

    @Test
    fun `a kitchen that already holds something is an existing user and is marked done unseen`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = FakePantryPort(listOf(holding("rice"))))
            advanceUntilIdle()

            assertEquals(OnboardingStatus.DONE, viewModel.state.value.status)
            assertTrue(onboarding.completed.value)
        }

    @Test
    fun `a kitchen listener that ends without a word still shows the flow`() =
        runTest(dispatcher) {
            val viewModel = started(kitchens = FakeKitchenPort(initial = null))
            advanceUntilIdle()

            assertEquals(OnboardingStatus.SHOWING, viewModel.state.value.status)
        }

    @Test
    fun `the staples are the catalogue by name and a tap marks one chosen`() =
        runTest(dispatcher) {
            val viewModel = started()
            catalogue.emit(listOf(ingredient("rice", "Rice"), ingredient("egg", "Egg")))
            advanceUntilIdle()

            viewModel.toggle(id("rice"))

            assertEquals(listOf("Egg", "Rice"), viewModel.state.value.staples.map { it.label })
            assertEquals(listOf(false, true), viewModel.state.value.staples.map { it.isChosen })
            assertEquals(1, viewModel.state.value.chosenCount)
        }

    @Test
    fun `leaving the staples page adds the chosen ones and moves on`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort()
            val viewModel = started(pantry = pantry)
            catalogue.emit(listOf(ingredient("rice", "Rice"), ingredient("egg", "Egg")))
            advanceUntilIdle()

            viewModel.next()
            viewModel.toggle(id("rice"))
            viewModel.toggle(id("egg"))
            viewModel.next()
            advanceUntilIdle()

            assertEquals(OnboardingStep.PREFERENCES, viewModel.state.value.step)
            assertEquals(setOf("rice", "egg"), pantry.held.mapNotNull { it.ingredient?.value }.toSet())
        }

    @Test
    fun `nothing chosen moves on without writing`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort()
            val viewModel = started(pantry = pantry)
            advanceUntilIdle()

            viewModel.next()
            viewModel.next()
            advanceUntilIdle()

            assertEquals(OnboardingStep.PREFERENCES, viewModel.state.value.step)
            assertTrue(pantry.held.isEmpty())
        }

    @Test
    fun `a refused add stays on the staples page and says why`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = FakePantryPort(readError = AppError.Network()))
            catalogue.emit(listOf(ingredient("rice", "Rice")))
            advanceUntilIdle()

            viewModel.next()
            viewModel.toggle(id("rice"))
            viewModel.next()
            advanceUntilIdle()

            assertEquals(OnboardingStep.STAPLES, viewModel.state.value.step)
            assertNotNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isAdding)
        }

    @Test
    fun `back returns a page and does nothing on the first`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            viewModel.back()
            assertEquals(OnboardingStep.VALUE, viewModel.state.value.step)
            viewModel.next()
            viewModel.back()
            assertEquals(OnboardingStep.VALUE, viewModel.state.value.step)
        }

    @Test
    fun `skipping from any page ends it for good and opens ideas without adding what was chosen`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort()
            val viewModel = started(pantry = pantry)
            catalogue.emit(listOf(ingredient("rice", "Rice")))
            advanceUntilIdle()
            viewModel.next()
            viewModel.toggle(id("rice"))

            viewModel.exits.test {
                viewModel.skip()
                assertEquals(OnboardingExit.IDEAS, awaitItem())
            }

            assertEquals(OnboardingStatus.DONE, viewModel.state.value.status)
            assertTrue(onboarding.completed.value)
            assertTrue(pantry.held.isEmpty())
        }

    @Test
    fun `setting preferences ends it and opens the profile`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()
            viewModel.next()
            viewModel.next()
            advanceUntilIdle()

            viewModel.exits.test {
                viewModel.setPreferences()
                assertEquals(OnboardingExit.PREFERENCES, awaitItem())
            }
            assertTrue(onboarding.completed.value)
        }

    @Test
    fun `not now on the last page opens ideas`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()
            viewModel.next()
            viewModel.next()
            advanceUntilIdle()

            viewModel.exits.test {
                viewModel.next()
                assertEquals(OnboardingExit.IDEAS, awaitItem())
            }
        }

    private fun started(
        kitchens: FakeKitchenPort = FakeKitchenPort(),
        pantry: FakePantryPort = FakePantryPort(),
    ): OnboardingViewModel {
        var next = 0
        return OnboardingViewModel(
            reads =
                OnboardingReadsDelegate(
                    completed = ObserveOnboardingCompletedUseCase(onboarding),
                    kitchen = ObserveKitchenUseCase(kitchens),
                    pantry = ObservePantryUseCase(pantry),
                    ingredients = ObserveIngredientsUseCase(catalogue),
                ),
            writes =
                OnboardingWritesDelegate(
                    complete = CompleteOnboardingUseCase(onboarding),
                    addStaples = AddStaplesUseCase(pantry, IdGenerator { "item-${++next}" }, TimeProvider { now }),
                ),
        ).also { it.start(user, listOf("en")) }
    }

    private fun id(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

    private fun ingredient(
        raw: String,
        label: String,
    ): Ingredient = Ingredient(id(raw), mapOf("en" to label), defaultUnit = null, tags = emptyList())

    private fun holding(raw: String): PantryItem =
        PantryItem(
            id = (PantryItemId.of("held-$raw") as AppResult.Success).data,
            ingredient = id(raw),
            freeText = null,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = null,
            updatedAt = now,
        )

    private companion object {
        val now: Instant = Instant.fromEpochSeconds(0)
    }
}

private class FakeOnboardingPort : OnboardingRepositoryContract {
    val completed = MutableStateFlow(false)

    override fun observeCompleted(): Flow<Boolean> = completed

    override suspend fun markCompleted(): AppResult<Unit> {
        completed.value = true
        return AppResult.Success(Unit)
    }
}
