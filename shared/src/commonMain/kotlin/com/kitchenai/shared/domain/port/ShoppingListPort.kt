package com.kitchenai.shared.domain.port

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
 * The observers emit an [AppResult] rather than a bare list: a stream that fails mid-flight must
 * not do it by throwing either. Every mutation is an upsert of a whole document, which is what
 * makes a repeated write harmless.
 */
interface ShoppingListPort {
    fun observeLists(userId: UserId): Flow<AppResult<List<ShoppingList>>>

    fun observeItems(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<AppResult<List<ShoppingItem>>>

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
