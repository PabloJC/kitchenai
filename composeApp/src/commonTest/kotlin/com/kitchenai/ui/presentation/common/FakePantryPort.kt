package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Holdings a test can set, and a read that can fail. */
class FakePantryPort(
    items: List<PantryItem> = emptyList(),
    private val readError: AppError? = null,
) : PantryRepositoryContract {
    private val state = MutableStateFlow(items)

    val held: List<PantryItem> get() = state.value

    /** One-shot reads, counted: a superseded load is one that never got this far. */
    var reads = 0
        private set

    override fun observePantry(kitchenId: KitchenId): Flow<List<PantryItem>> =
        if (readError == null) state else emptyFlow()

    override fun pantryErrors(kitchenId: KitchenId): Flow<AppError> = emptyFlow()

    override suspend fun getPantry(kitchenId: KitchenId): AppResult<List<PantryItem>> {
        reads++
        return readError?.let { AppResult.Failure(it) } ?: AppResult.Success(state.value)
    }

    override suspend fun upsert(
        kitchenId: KitchenId,
        item: PantryItem,
    ): AppResult<Unit> {
        state.value = state.value.filterNot { it.id == item.id } + item
        return AppResult.Success(Unit)
    }

    override suspend fun remove(
        kitchenId: KitchenId,
        id: PantryItemId,
    ): AppResult<Unit> {
        state.value = state.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun upsertAll(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ): AppResult<Unit> {
        val replaced = items.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in replaced } + items
        return AppResult.Success(Unit)
    }

    override suspend fun upsertAllConfirmed(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ): AppResult<Unit> = upsertAll(kitchenId, items)
}
