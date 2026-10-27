package com.kitchenai.shared.domain.usecase.reminder

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.port.ReminderSettingsRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSettingsUseCasesTest {
    private val repository = FakeReminderSettingsRepository()

    @Test
    fun `nothing saved reads as the defaults`() =
        runTest {
            assertEquals(ReminderSettings(), ObserveReminderSettingsUseCase(repository)().first())
        }

    @Test
    fun `a saved choice is seen by observers`() =
        runTest {
            val chosen = ReminderSettings(enabled = false, hour = 8, minute = 15, offered = true)

            SaveReminderSettingsUseCase(repository)(chosen)

            assertEquals(chosen, ObserveReminderSettingsUseCase(repository)().first())
        }

    @Test
    fun `a time that is not on a clock is refused and nothing is stored`() =
        runTest {
            val result = SaveReminderSettingsUseCase(repository)(ReminderSettings(hour = 24))

            assertTrue(result is AppResult.Failure && result.error is AppError.Validation)
            assertEquals(ReminderSettings(), repository.current.value)
        }
}

private class FakeReminderSettingsRepository : ReminderSettingsRepositoryContract {
    val current = MutableStateFlow(ReminderSettings())

    override fun observeSettings(): Flow<ReminderSettings> = current

    override suspend fun saveSettings(settings: ReminderSettings): AppResult<Unit> {
        current.value = settings
        return AppResult.Success(Unit)
    }
}
