package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.ShoppingListPort
import com.kitchenai.shared.domain.port.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * In-memory store with the convergence properties the real adapter must have: an upsert
 * replaces a document by id and a repeated upsert changes nothing.
 */
class FakeShoppingListPort(
    private val failure: AppError? = null,
    // Which item listener broke. Null means all of them, which is what most tests want.
    private val failingList: ShoppingListId? = null,
) : ShoppingListPort {
    private val lists = MutableStateFlow<List<ShoppingList>>(emptyList())
    private val items = MutableStateFlow<Map<String, List<ShoppingItem>>>(emptyMap())

    var upsertListCalls: Int = 0
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
    override fun observeLists(userId: UserId): Flow<List<ShoppingList>> = if (failure == null) lists else emptyFlow()

    override fun observeItems(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>> = if (broken(listId)) emptyFlow() else items.map { it[listId.value].orEmpty() }

    override fun listErrors(userId: UserId): Flow<AppError> = failure?.let { flowOf(it) } ?: emptyFlow()

    override fun itemErrors(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<AppError> = if (broken(listId)) flowOf(failure!!) else emptyFlow()

    private fun broken(listId: ShoppingListId): Boolean =
        failure != null && (failingList == null || failingList == listId)

    override suspend fun getLists(userId: UserId): AppResult<List<ShoppingList>> = read(lists.value)

    override suspend fun getItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<List<ShoppingItem>> = read(items.value[listId.value].orEmpty())

    override suspend fun upsertList(
        userId: UserId,
        list: ShoppingList,
    ): AppResult<Unit> =
        write {
            upsertListCalls++
            lists.value = lists.value.filterNot { it.id == list.id } + list
        }

    override suspend fun upsertItems(
        userId: UserId,
        listId: ShoppingListId,
        items: List<ShoppingItem>,
    ): AppResult<Unit> =
        write {
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

/** A valid unchecked line, named after its own id so that assertions read by id. */
fun shoppingItem(
    id: String,
    ingredient: String? = id,
    freeText: String? = null,
    quantity: Quantity? = null,
    seconds: Long = 1_000,
): ShoppingItem =
    (
        ShoppingItem.create(
            id = itemId(id),
            updatedAt = instant(seconds),
            ingredient = ingredient?.let(::ingredientId),
            freeText = freeText,
            quantity = quantity,
        ) as AppResult.Success
    ).data

fun userId(raw: String = "user"): UserId = (UserId.of(raw) as AppResult.Success).data

fun listId(raw: String = "list"): ShoppingListId = (ShoppingListId.of(raw) as AppResult.Success).data

fun itemId(raw: String): ShoppingItemId = (ShoppingItemId.of(raw) as AppResult.Success).data

fun ingredientId(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

/** Units are opaque taxonomy terms; nothing in the domain or in a fixture may name one. */
fun termRef(
    taxonomy: String,
    term: String,
): TermRef =
    TermRef(
        (TaxonomyId.of(taxonomy) as AppResult.Success).data,
        (TermId.of(term) as AppResult.Success).data,
    )

fun instant(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

fun fixedTime(seconds: Long): TimeProvider = TimeProvider { instant(seconds) }

fun sequentialIds(prefix: String = "generated"): IdGenerator {
    var next = 0
    return IdGenerator { "$prefix-${++next}" }
}
