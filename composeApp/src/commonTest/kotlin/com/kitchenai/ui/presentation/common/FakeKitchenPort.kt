package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf

/** The one kitchen a screen opens into. Every screen ViewModel derives its `KitchenId` from it. */
val defaultKitchenId: KitchenId = (KitchenId.of("kitchen-1") as AppResult.Success).data

/**
 * A kitchen already exists by the time a screen mounts — the session gate ensures one — so this
 * defaults to one rather than to none, unlike [FakeShoppingListPort]'s default list.
 */
class FakeKitchenPort(
    initial: Kitchen? = kitchen(),
    private val readError: AppError? = null,
) : KitchenRepositoryContract {
    private val state = MutableStateFlow(initial)

    override fun observeMyKitchen(userId: UserId): Flow<Kitchen> =
        if (readError != null || state.value == null) emptyFlow() else state.filterNotNull()

    override fun kitchenErrors(userId: UserId): Flow<AppError> = readError?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun getMyKitchen(userId: UserId): AppResult<Kitchen> =
        readError?.let { AppResult.Failure(it) }
            ?: state.value?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound("kitchen"))

    override suspend fun createKitchen(
        ownerId: UserId,
        displayName: String?,
    ): AppResult<Kitchen> = kitchen(ownerId = ownerId).also { state.value = it }.let { AppResult.Success(it) }

    override suspend fun joinKitchen(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen> = AppResult.Failure(AppError.Unknown())

    override suspend fun leaveKitchen(
        userId: UserId,
        kitchenId: KitchenId,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun removeMember(
        kitchenId: KitchenId,
        requesterId: UserId,
        memberId: UserId,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun regenerateJoinCode(
        kitchenId: KitchenId,
        requesterId: UserId,
    ): AppResult<Kitchen> = AppResult.Failure(AppError.Unknown())

    override suspend fun updateMyDisplayName(
        userId: UserId,
        kitchenId: KitchenId,
        displayName: String,
    ): AppResult<Unit> = AppResult.Success(Unit)

    /** Pushes a new kitchen to an already-active collector, for a screen open while it changes. */
    fun emit(kitchen: Kitchen) {
        state.value = kitchen
    }
}

fun kitchen(
    id: KitchenId = defaultKitchenId,
    ownerId: UserId = (UserId.of("kitchen-owner").let { it as AppResult.Success }).data,
    joinCode: String = "code-1",
): Kitchen = Kitchen(id, ownerId, setOf(ownerId), (KitchenJoinCode.of(joinCode) as AppResult.Success).data, emptyMap())
