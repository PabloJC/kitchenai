package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingItemPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Items keyed by list, so a test can break one list's listener and leave the others alone. */
class FakeShoppingItemPort(
    private val failure: AppError? = null,
    // Which item listener broke. Null means all of them, which is what most tests want.
    private val failingList: ShoppingListId? = null,
) : ShoppingItemPort {
    private val items = MutableStateFlow<Map<String, List<ShoppingItem>>>(emptyMap())

    var upsertCalls: Int = 0
        private set

    fun itemsOf(listId: ShoppingListId): List<ShoppingItem> = items.value[listId.value].orEmpty()

    fun seed(
        listId: ShoppingListId,
        vararg seeded: ShoppingItem,
    ) {
        items.value = items.value + (listId.value to seeded.toList())
    }

    // A failing listener stops emitting and reports on its keyed error stream, which is what
    // the real adapter does with a Firestore snapshot error.
    override fun observeItems(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>> = if (broken(listId)) emptyFlow() else items.map { it[listId.value].orEmpty() }

    override fun itemErrors(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<AppError> = if (broken(listId)) flowOf(failure!!) else emptyFlow()

    override suspend fun getItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<List<ShoppingItem>> = read(items.value[listId.value].orEmpty())

    override suspend fun upsertItems(
        userId: UserId,
        listId: ShoppingListId,
        items: List<ShoppingItem>,
    ): AppResult<Unit> =
        write {
            upsertCalls++
            val ids = items.map { it.id }.toSet()
            replace(listId) { current -> current.filterNot { it.id in ids } + items }
        }

    override suspend fun removeItem(
        userId: UserId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
    ): AppResult<Unit> = write { replace(listId) { current -> current.filterNot { it.id == itemId } } }

    override suspend fun removeCheckedItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<Unit> = write { replace(listId) { current -> current.filterNot { it.checked } } }

    private fun broken(listId: ShoppingListId): Boolean =
        failure != null && (failingList == null || failingList == listId)

    private fun replace(
        listId: ShoppingListId,
        transform: (List<ShoppingItem>) -> List<ShoppingItem>,
    ) {
        items.value = items.value + (listId.value to transform(items.value[listId.value].orEmpty()))
    }

    private fun <T> read(value: T): AppResult<T> = failure?.let { AppResult.Failure(it) } ?: AppResult.Success(value)

    private fun write(block: () -> Unit): AppResult<Unit> =
        failure?.let { AppResult.Failure(it) } ?: AppResult.Success(block())
}
