package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import kotlinx.coroutines.CompletableDeferred

/**
 * A pantry whose one-shot reads a test can fail, or hold back once: the observed holdings keep
 * flowing, which is how a re-match is made to fail or to land late.
 */
class FlakyPantryPort(
    private val inner: FakePantryPort = FakePantryPort(),
) : PantryRepositoryContract by inner {
    var failReads = false

    /** Taken by the next read only, then cleared, so later reads are not held back too. */
    var holdNextRead: CompletableDeferred<Unit>? = null

    override suspend fun getPantry(kitchenId: KitchenId): AppResult<List<PantryItem>> {
        holdNextRead?.let { gate ->
            holdNextRead = null
            gate.await()
        }
        return if (failReads) AppResult.Failure(AppError.Network()) else inner.getPantry(kitchenId)
    }
}
