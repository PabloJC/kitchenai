package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.ShoppingList
import kotlinx.coroutines.flow.Flow

/**
 * One kitchen's shopping lists. The items inside a list are a separate collection and a separate
 * port — see [ShoppingItemRepositoryContract].
 *
 * The observer emits data only and a failing listener stops emitting: it never throws, and the
 * failure travels on [listErrors]. Every mutation is an upsert of a whole document, which is
 * what makes a repeated write harmless.
 */
interface ShoppingListRepositoryContract {
    fun observeLists(kitchenId: KitchenId): Flow<List<ShoppingList>>

    /** Failures of the listener above, which stops emitting rather than throwing. */
    fun listErrors(kitchenId: KitchenId): Flow<AppError>

    /**
     * One-shot read for the read-modify-write use cases: taking the first emission of the
     * listener would hang forever once that listener has failed.
     */
    suspend fun getLists(kitchenId: KitchenId): AppResult<List<ShoppingList>>

    suspend fun upsertList(
        kitchenId: KitchenId,
        list: ShoppingList,
    ): AppResult<Unit>
}
