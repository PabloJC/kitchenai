package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingListRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Instant

/** The one list the app ensures on first launch. */
val defaultListId: ShoppingListId = (ShoppingListId.of("list-1") as AppResult.Success).data

/**
 * The shopping collections, shared by the screens that write to them. Extracted rather than
 * copied: this is the third screen that needs them. The list already exists, which is the state
 * a screen opens in — the session gate created it.
 */
class FakeShoppingListPort(
    /** Empty puts a screen in the state the session gate has not reached yet: no list at all. */
    private val existing: Boolean = true,
) : ShoppingListRepositoryContract {
    val created = mutableListOf<ShoppingList>()

    override fun observeLists(kitchenId: KitchenId): Flow<List<ShoppingList>> = emptyFlow()

    override fun listErrors(kitchenId: KitchenId): Flow<AppError> = emptyFlow()

    override suspend fun getLists(kitchenId: KitchenId): AppResult<List<ShoppingList>> =
        if (!existing) {
            AppResult.Success(emptyList())
        } else {
            AppResult.Success(listOf(ShoppingList(defaultListId, kitchenId, emptyMap(), Instant.fromEpochSeconds(0))))
        }

    override suspend fun upsertList(
        kitchenId: KitchenId,
        list: ShoppingList,
    ): AppResult<Unit> {
        created += list
        return AppResult.Success(Unit)
    }
}

/**
 * Writes are recorded and never echoed: what a ViewModel renders has to come from the stream,
 * so a fake that wrote back would hide exactly the bug these screens can have.
 */
class FakeShoppingItemPort : ShoppingItemRepositoryContract {
    private val stream = MutableSharedFlow<List<ShoppingItem>>(replay = 1)
    private var current: List<ShoppingItem> = emptyList()

    val errors = MutableSharedFlow<AppError>()
    val upserts = mutableListOf<List<ShoppingItem>>()
    var upsertResult: AppResult<Unit> = AppResult.Success(Unit)
    var removed: ShoppingItemId? = null
    val removedBatch = mutableListOf<ShoppingItemId>()
    var clears = 0

    suspend fun emit(items: List<ShoppingItem>) {
        current = items
        stream.emit(items)
    }

    override fun observeItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>> = stream

    override fun itemErrors(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): Flow<AppError> = errors

    override suspend fun getItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): AppResult<List<ShoppingItem>> = AppResult.Success(current)

    override suspend fun upsertItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        items: List<ShoppingItem>,
    ): AppResult<Unit> {
        upserts += items
        return upsertResult
    }

    override suspend fun removeItem(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
    ): AppResult<Unit> {
        removed = itemId
        return AppResult.Success(Unit)
    }

    override suspend fun removeItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        ids: List<ShoppingItemId>,
    ): AppResult<Unit> {
        removedBatch += ids
        return AppResult.Success(Unit)
    }

    override suspend fun removeCheckedItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): AppResult<Unit> {
        clears++
        return AppResult.Success(Unit)
    }
}
