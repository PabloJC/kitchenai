package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.usecase.reminder.ObserveReminderSettingsUseCase
import com.kitchenai.shared.domain.usecase.reminder.SaveReminderSettingsUseCase
import com.kitchenai.ui.presentation.common.FakeReminderSettingsPort
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeReminderSettingsPort()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `never asked reads as off rather than as the stored default`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.enabled)
        }

    @Test
    fun `turning on with the permission granted stores it as answered`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setEnabled(on = true, granted = true)
            advanceUntilIdle()

            assertEquals(ReminderSettings(enabled = true, offered = true), settings.current.value)
            assertTrue(viewModel.state.value.enabled)
        }

    @Test
    fun `a refused permission keeps the switch off and says where to allow it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setEnabled(on = true, granted = false)
            advanceUntilIdle()

            assertEquals(ReminderSettings(enabled = false, offered = true), settings.current.value)
            assertFalse(viewModel.state.value.enabled)
            assertTrue(viewModel.state.value.permissionRefused)
        }

    @Test
    fun `a new time is stored and the switch stays as it was`() =
        runTest(dispatcher) {
            settings.current.value = ReminderSettings(enabled = true, offered = true)
            val viewModel = viewModel()

            viewModel.setTime(hour = 7, minute = 45)
            advanceUntilIdle()

            val expected = ReminderSettings(enabled = true, hour = 7, minute = 45, offered = true)
            assertEquals(expected, settings.current.value)
            assertEquals(7, viewModel.state.value.hour)
        }

    @Test
    fun `a write that fails is reported on the rows`() =
        runTest(dispatcher) {
            settings.failWith = AppError.Unauthorized()
            val viewModel = viewModel()

            viewModel.setEnabled(on = false)
            advanceUntilIdle()

            assertNotNull(viewModel.state.value.error)
        }

    private fun viewModel(): ReminderSettingsViewModel =
        ReminderSettingsViewModel(ObserveReminderSettingsUseCase(settings), SaveReminderSettingsUseCase(settings))
}
