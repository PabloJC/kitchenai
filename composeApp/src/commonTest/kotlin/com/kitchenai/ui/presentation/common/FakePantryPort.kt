package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.UserId
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

    override fun observePantry(userId: UserId): Flow<List<PantryItem>> = if (readError == null) state else emptyFlow()

    override fun pantryErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getPantry(userId: UserId): AppResult<List<PantryItem>> {
        reads++
        return readError?.let { AppResult.Failure(it) } ?: AppResult.Success(state.value)
    }

    override suspend fun upsert(
        userId: UserId,
        item: PantryItem,
    ): AppResult<Unit> {
        state.value = state.value.filterNot { it.id == item.id } + item
        return AppResult.Success(Unit)
    }

    override suspend fun remove(
        userId: UserId,
        id: PantryItemId,
    ): AppResult<Unit> {
        state.value = state.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun upsertAll(
        userId: UserId,
        items: List<PantryItem>,
    ): AppResult<Unit> {
        val replaced = items.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in replaced } + items
        return AppResult.Success(Unit)
    }

    override suspend fun upsertAllConfirmed(
        userId: UserId,
        items: List<PantryItem>,
    ): AppResult<Unit> = upsertAll(userId, items)
}
