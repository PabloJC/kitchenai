package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/**
 * Storage for one user's shopping lists, as the domain needs it. `data` implements it.
 *
 * The observers emit data only and a failing listener stops emitting: it never throws, and the
 * failure travels on the error stream keyed like the observer it belongs to, so one list's
 * broken listener is not reported to another. Every mutation is an upsert of a whole document, which is
 * what makes a repeated write harmless.
 */
interface ShoppingListPort {
    fun observeLists(userId: UserId): Flow<List<ShoppingList>>

    fun observeItems(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>>

    /** Failures of the listeners above, keyed like them: a broken list is not every list. */
    fun listErrors(userId: UserId): Flow<AppError>

    fun itemErrors(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<AppError>

    /**
     * One-shot reads for the read-modify-write use cases: taking the first emission of a
     * listener would hang forever once that listener has failed.
     */
    suspend fun getLists(userId: UserId): AppResult<List<ShoppingList>>

    suspend fun getItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<List<ShoppingItem>>

    suspend fun upsertList(
        userId: UserId,
        list: ShoppingList,
    ): AppResult<Unit>

    suspend fun upsertItem(
        userId: UserId,
        listId: ShoppingListId,
        item: ShoppingItem,
    ): AppResult<Unit>

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
