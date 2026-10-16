package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Appearance
import kotlinx.coroutines.flow.Flow

/** The appearance chosen on this device; a phone and a tablet sharing a kitchen may each want their own. */
interface AppearanceRepositoryContract {
    /** Emits the stored choice at once, and [Appearance.SYSTEM] when nothing was ever chosen. */
    fun observeAppearance(): Flow<Appearance>

    suspend fun saveAppearance(appearance: Appearance): AppResult<Unit>
}
