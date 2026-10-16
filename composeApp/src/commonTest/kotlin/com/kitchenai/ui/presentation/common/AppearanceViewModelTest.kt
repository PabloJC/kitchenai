package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Appearance
import com.kitchenai.shared.domain.port.AppearanceRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.ObserveAppearanceUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveAppearanceUseCase
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAppearanceRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the stored choice reaches the state and decides dark or light`() =
        runTest(dispatcher) {
            repository.current.value = Appearance.DARK
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(Appearance.DARK, viewModel.state.value.appearance)
            assertTrue(viewModel.state.value.isDark(systemIsDark = false))
        }

    @Test
    fun `following the system answers with the system`() {
        val state = AppearanceUiState(Appearance.SYSTEM)

        assertTrue(state.isDark(systemIsDark = true))
        assertFalse(state.isDark(systemIsDark = false))
        assertFalse(AppearanceUiState(Appearance.LIGHT).isDark(systemIsDark = true))
    }

    @Test
    fun `choosing stores the appearance and the state follows`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.choose(Appearance.LIGHT)
            advanceUntilIdle()

            assertEquals(Appearance.LIGHT, repository.current.value)
            assertEquals(Appearance.LIGHT, viewModel.state.value.appearance)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `a choice that cannot be stored says so and keeps the last one`() =
        runTest(dispatcher) {
            repository.saveResult = AppResult.Failure(AppError.Network())
            val viewModel = viewModel()

            viewModel.choose(Appearance.DARK)
            advanceUntilIdle()

            assertEquals(Appearance.SYSTEM, viewModel.state.value.appearance)
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)
        }

    private fun viewModel(): AppearanceViewModel =
        AppearanceViewModel(ObserveAppearanceUseCase(repository), SaveAppearanceUseCase(repository))
}

private class FakeAppearanceRepository : AppearanceRepositoryContract {
    val current = MutableStateFlow(Appearance.SYSTEM)
    var saveResult: AppResult<Unit> = AppResult.Success(Unit)

    override fun observeAppearance(): Flow<Appearance> = current

    override suspend fun saveAppearance(appearance: Appearance): AppResult<Unit> {
        if (saveResult is AppResult.Success) current.value = appearance
        return saveResult
    }
}
