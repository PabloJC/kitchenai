package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import kotlinx.coroutines.flow.Flow

/**
 * The items inside one shopping list — a subcollection of it, and its own aggregate.
 *
 * Everything here is keyed by `listId`, including [itemErrors]: one list's broken listener
 * must not be reported to a screen watching another list.
 */
interface ShoppingItemRepositoryContract {
    fun observeItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>>

    /** Failures of the listener above, keyed like it: a broken list is not every list. */
    fun itemErrors(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): Flow<AppError>

    /**
     * One-shot read for the read-modify-write use cases: taking the first emission of the
     * listener would hang forever once that listener has failed.
     */
    suspend fun getItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): AppResult<List<ShoppingItem>>

    /** One method, not two: a single-item write is a batch of one, and adds no capability. */
    suspend fun upsertItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        items: List<ShoppingItem>,
    ): AppResult<Unit>

    suspend fun removeItem(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
    ): AppResult<Unit>

    /** A named batch rather than repeated [removeItem] calls: moving five lines is one round trip. */
    suspend fun removeItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        ids: List<ShoppingItemId>,
    ): AppResult<Unit>

    suspend fun removeCheckedItems(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): AppResult<Unit>
}
