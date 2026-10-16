package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Appearance
import com.kitchenai.shared.domain.port.AppearanceRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppearanceUseCasesTest {
    private val repository = FakeAppearanceRepository()

    @Test
    fun `observing reads the stored appearance`() =
        runTest {
            assertEquals(Appearance.SYSTEM, ObserveAppearanceUseCase(repository)().first())
        }

    @Test
    fun `saving hands the choice to the repository and observers see it`() =
        runTest {
            SaveAppearanceUseCase(repository)(Appearance.LIGHT)

            assertEquals(Appearance.LIGHT, ObserveAppearanceUseCase(repository)().first())
        }
}

private class FakeAppearanceRepository : AppearanceRepositoryContract {
    private val current = MutableStateFlow(Appearance.SYSTEM)

    override fun observeAppearance(): Flow<Appearance> = current

    override suspend fun saveAppearance(appearance: Appearance): AppResult<Unit> {
        current.value = appearance
        return AppResult.Success(Unit)
    }
}
