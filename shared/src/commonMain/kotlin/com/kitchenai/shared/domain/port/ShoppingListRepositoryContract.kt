package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/**
 * One user's shopping lists. The items inside a list are a separate collection and a separate
 * port — see [ShoppingItemRepositoryContract].
 *
 * The observer emits data only and a failing listener stops emitting: it never throws, and the
 * failure travels on [listErrors]. Every mutation is an upsert of a whole document, which is
 * what makes a repeated write harmless.
 */
interface ShoppingListRepositoryContract {
    fun observeLists(userId: UserId): Flow<List<ShoppingList>>

    /** Failures of the listener above, which stops emitting rather than throwing. */
    fun listErrors(userId: UserId): Flow<AppError>

    /**
     * One-shot read for the read-modify-write use cases: taking the first emission of the
     * listener would hang forever once that listener has failed.
     */
    suspend fun getLists(userId: UserId): AppResult<List<ShoppingList>>

    suspend fun upsertList(
        userId: UserId,
        list: ShoppingList,
    ): AppResult<Unit>
}
