package com.kitchenai.ui.presentation.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

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
    fun `it starts online so no banner flashes before the monitor answers`() {
        val viewModel = ConnectivityViewModel(FakeNetworkMonitor(initiallyOnline = false))

        assertEquals(ConnectivityUiState(isOnline = true), viewModel.state.value)
    }

    @Test
    fun `going offline and coming back follows the monitor`() =
        runTest(dispatcher) {
            val monitor = FakeNetworkMonitor()
            val viewModel = ConnectivityViewModel(monitor)
            viewModel.state.launchIn(backgroundScope)
            advanceUntilIdle()

            monitor.setOnline(false)
            advanceUntilIdle()
            assertEquals(false, viewModel.state.value.isOnline)

            monitor.setOnline(true)
            advanceUntilIdle()
            assertEquals(true, viewModel.state.value.isOnline)
        }

    @Test
    fun `a device that is already offline reads as offline once collected`() =
        runTest(dispatcher) {
            val viewModel = ConnectivityViewModel(FakeNetworkMonitor(initiallyOnline = false))
            viewModel.state.launchIn(backgroundScope)
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.isOnline)
        }
}
