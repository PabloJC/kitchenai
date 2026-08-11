package com.kitchenai.ui.presentation.health

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.port.HealthCheckPort
import com.kitchenai.shared.domain.usecase.CheckFirebaseHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {
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
    fun `starts in Loading and moves to Ready with the projectId`() =
        runTest(dispatcher) {
            val viewModel = viewModelReturning(AppResult.Success("test-project"))

            viewModel.state.test {
                assertEquals(HealthUiState.Loading, awaitItem())
                assertEquals(HealthUiState.Ready("test-project"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failure from the use case ends in Error`() =
        runTest(dispatcher) {
            val viewModel = viewModelReturning(AppResult.Failure(AppError.Unknown()))

            viewModel.state.test {
                assertEquals(HealthUiState.Loading, awaitItem())
                assertEquals(HealthUiState.Error("Firebase did not start"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the error message does not include the cause`() =
        runTest(dispatcher) {
            val cause = IllegalStateException("/Users/alguien/kitchenai: token abc123")
            val viewModel = viewModelReturning(AppResult.Failure(AppError.Unknown(cause)))

            viewModel.state.test {
                awaitItem()
                val state = awaitItem()

                // The cause can carry paths, e-mails or identifiers.
                assertEquals(HealthUiState.Error("Firebase did not start"), state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun viewModelReturning(result: AppResult<String>) =
        HealthViewModel(
            CheckFirebaseHealth(
                HealthCheckPort {
                    result
                },
            ),
        )
}
