package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.local.AppearanceLocalDataSource
import com.kitchenai.shared.data.local.localCall
import com.kitchenai.shared.domain.model.Appearance
import com.kitchenai.shared.domain.port.AppearanceRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A value an older build wrote, or one nobody did, reads as following the system rather than failing. */
class AppearanceRepository(
    private val local: AppearanceLocalDataSource,
    private val dispatchers: DispatcherProvider,
) : AppearanceRepositoryContract {
    override fun observeAppearance(): Flow<Appearance> =
        local.stored.map { raw -> Appearance.entries.firstOrNull { it.name == raw } ?: Appearance.SYSTEM }

    override suspend fun saveAppearance(appearance: Appearance): AppResult<Unit> =
        localCall(dispatchers) { local.write(appearance.name) }
}
