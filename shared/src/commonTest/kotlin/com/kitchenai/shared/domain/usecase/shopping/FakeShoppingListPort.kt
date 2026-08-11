package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingListPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory store with the convergence properties the real adapter must have: an upsert
 * replaces a document by id and a repeated upsert changes nothing.
 */
class FakeShoppingListPort(
    private val failure: AppError? = null,
) : ShoppingListPort {
    private val lists = MutableStateFlow<List<ShoppingList>>(emptyList())

    var upsertListCalls: Int = 0
        private set

    // A failing listener stops emitting and reports on its error stream, which is what the
    // real adapter does with a Firestore snapshot error.
    override fun observeLists(userId: UserId): Flow<List<ShoppingList>> = if (failure == null) lists else emptyFlow()

    override fun listErrors(userId: UserId): Flow<AppError> = failure?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun getLists(userId: UserId): AppResult<List<ShoppingList>> =
        failure?.let { AppResult.Failure(it) } ?: AppResult.Success(lists.value)

    override suspend fun upsertList(
        userId: UserId,
        list: ShoppingList,
    ): AppResult<Unit> =
        failure?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit).also {
            upsertListCalls++
            lists.value = lists.value.filterNot { held -> held.id == list.id } + list
        }
}
