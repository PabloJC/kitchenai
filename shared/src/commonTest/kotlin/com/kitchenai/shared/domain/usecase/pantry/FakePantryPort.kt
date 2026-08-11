package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * In-memory [PantryPort] that counts its writes, so a test can prove that a batched call
 * stays a single round trip.
 */
class FakePantryPort(
    initial: List<PantryItem> = emptyList(),
    private val readError: AppError? = null,
    private val writeError: AppError? = null,
) : PantryPort {
    private val state = MutableStateFlow(initial)

    var upsertCalls = 0
        private set
    var upsertAllCalls = 0
        private set
    val removed = mutableListOf<PantryItemId>()
    val items: List<PantryItem> get() = state.value

    override fun observePantry(userId: UserId): Flow<AppResult<List<PantryItem>>> =
        state.map { current -> readError?.let { AppResult.Failure(it) } ?: AppResult.Success(current) }

    override suspend fun upsert(
        userId: UserId,
        item: PantryItem,
    ): AppResult<Unit> {
        upsertCalls++
        return write { held -> held.filterNot { it.id == item.id } + item }
    }

    override suspend fun remove(
        userId: UserId,
        id: PantryItemId,
    ): AppResult<Unit> {
        removed += id
        return write { held -> held.filterNot { it.id == id } }
    }

    override suspend fun upsertAll(
        userId: UserId,
        items: List<PantryItem>,
    ): AppResult<Unit> {
        upsertAllCalls++
        val ids = items.map { it.id }.toSet()
        return write { held -> held.filterNot { it.id in ids } + items }
    }

    private fun write(edit: (List<PantryItem>) -> List<PantryItem>): AppResult<Unit> =
        writeError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit).also { state.value = edit(state.value) }
}

// Fixtures. Every identifier here is opaque on purpose: naming a unit, a location or an
// ingredient in a fixture is the same mistake as naming it in code.
internal val user: UserId = (UserId.of("user-1") as AppResult.Success).data

internal fun pantryItemId(raw: String): PantryItemId = (PantryItemId.of(raw) as AppResult.Success).data

internal fun ingredientId(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

internal fun termRef(term: String): TermRef =
    TermRef(
        (TaxonomyId.of("taxonomy-1") as AppResult.Success).data,
        (TermId.of(term) as AppResult.Success).data,
    )

internal fun pantryItem(
    id: String,
    ingredient: String,
    quantity: Quantity,
    expiresAt: Instant? = null,
    updatedAt: Instant = Instant.fromEpochSeconds(0),
): PantryItem = PantryItem(pantryItemId(id), ingredientId(ingredient), quantity, null, expiresAt, updatedAt)
