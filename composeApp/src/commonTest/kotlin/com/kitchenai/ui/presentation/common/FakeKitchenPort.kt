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

    // Configurable per test, defaulting to what every screen but the kitchen one itself expects:
    // joining and regenerating are not wired anywhere else, so a caller that never sets these
    // keeps seeing them refused.
    var joinResult: AppResult<Kitchen> = AppResult.Failure(AppError.Unknown())
    var leaveResult: AppResult<Unit> = AppResult.Success(Unit)
    var removeMemberResult: AppResult<Unit> = AppResult.Success(Unit)
    var regenerateResult: AppResult<Kitchen> = AppResult.Failure(AppError.Unknown())

    val joinCalls = mutableListOf<KitchenJoinCode>()
    var leaveCount = 0
        private set
    val removedMembers = mutableListOf<UserId>()
    var regenerateCount = 0
        private set

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
    ): AppResult<Kitchen> {
        joinCalls += joinCode
        val result = joinResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun leaveKitchen(
        userId: UserId,
        kitchenId: KitchenId,
    ): AppResult<Unit> {
        leaveCount++
        if (leaveResult is AppResult.Success) state.value = null
        return leaveResult
    }

    override suspend fun removeMember(
        kitchenId: KitchenId,
        requesterId: UserId,
        memberId: UserId,
    ): AppResult<Unit> {
        removedMembers += memberId
        return removeMemberResult
    }

    override suspend fun regenerateJoinCode(
        kitchenId: KitchenId,
        requesterId: UserId,
    ): AppResult<Kitchen> {
        regenerateCount++
        val result = regenerateResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

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
    memberIds: Set<UserId> = setOf(ownerId),
    memberDisplayNames: Map<String, String> = emptyMap(),
): Kitchen =
    Kitchen(id, ownerId, memberIds, (KitchenJoinCode.of(joinCode) as AppResult.Success).data, memberDisplayNames)
