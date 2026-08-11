package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/**
 * The items inside one shopping list — a subcollection of it, and its own aggregate.
 *
 * Everything here is keyed by `listId`, including [itemErrors]: one list's broken listener
 * must not be reported to a screen watching another list.
 */
interface ShoppingItemPort {
    fun observeItems(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>>

    /** Failures of the listener above, keyed like it: a broken list is not every list. */
    fun itemErrors(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<AppError>

    /**
     * One-shot read for the read-modify-write use cases: taking the first emission of the
     * listener would hang forever once that listener has failed.
     */
    suspend fun getItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<List<ShoppingItem>>

    /** One method, not two: a single-item write is a batch of one, and adds no capability. */
    suspend fun upsertItems(
        userId: UserId,
        listId: ShoppingListId,
        items: List<ShoppingItem>,
    ): AppResult<Unit>

    suspend fun removeItem(
        userId: UserId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
    ): AppResult<Unit>

    suspend fun removeCheckedItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<Unit>
}
