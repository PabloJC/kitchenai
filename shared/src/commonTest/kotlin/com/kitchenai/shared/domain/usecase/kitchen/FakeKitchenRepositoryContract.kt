package com.kitchenai.shared.domain.usecase.kitchen

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

/**
 * In-memory [KitchenRepositoryContract]. `getMyKitchen` is what the use cases under test read
 * "no kitchen yet" from ([AppError.NotFound]), kept distinct from [readError] ("the read
 * failed") — the two collapsed into the same `null` before decision #68 was applied here. Write
 * results are configured per test, like `FakeSessionPort`.
 */
class FakeKitchenRepositoryContract(
    initial: Kitchen? = null,
    private val readError: AppError? = null,
) : KitchenRepositoryContract {
    private val state = MutableStateFlow(initial)

    var createResult: AppResult<Kitchen> = AppResult.Failure(AppError.Unknown())
    var joinResult: AppResult<Kitchen> = AppResult.Failure(AppError.Unknown())
    var leaveResult: AppResult<Unit> = AppResult.Success(Unit)
    var removeMemberResult: AppResult<Unit> = AppResult.Success(Unit)
    var regenerateResult: AppResult<Kitchen> = AppResult.Failure(AppError.Unknown())
    var updateMyDisplayNameResult: AppResult<Unit> = AppResult.Success(Unit)

    var createCalls: Int = 0
        private set
    var joinCalls: Int = 0
        private set
    var regenerateCalls: Int = 0
        private set
    val leftKitchens = mutableListOf<KitchenId>()
    val removedMembers = mutableListOf<Pair<KitchenId, UserId>>()
    val updatedDisplayNames = mutableListOf<Triple<UserId, KitchenId, String>>()
    val current: Kitchen? get() = state.value

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
    ): AppResult<Kitchen> {
        createCalls++
        val result = createResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun joinKitchen(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen> {
        joinCalls++
        val result = joinResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun leaveKitchen(
        userId: UserId,
        kitchenId: KitchenId,
    ): AppResult<Unit> {
        leftKitchens += kitchenId
        if (leaveResult is AppResult.Success) state.value = null
        return leaveResult
    }

    override suspend fun removeMember(
        kitchenId: KitchenId,
        requesterId: UserId,
        memberId: UserId,
    ): AppResult<Unit> {
        removedMembers += kitchenId to memberId
        return removeMemberResult
    }

    override suspend fun regenerateJoinCode(
        kitchenId: KitchenId,
        requesterId: UserId,
    ): AppResult<Kitchen> {
        regenerateCalls++
        val result = regenerateResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun updateMyDisplayName(
        userId: UserId,
        kitchenId: KitchenId,
        displayName: String,
    ): AppResult<Unit> {
        updatedDisplayNames += Triple(userId, kitchenId, displayName)
        return updateMyDisplayNameResult
    }

    /** Pushes a new value to an already-active collector, for reactivity tests. */
    fun emit(kitchen: Kitchen) {
        state.value = kitchen
    }
}

// Fixtures. Every identifier here is opaque on purpose, same reasoning as the pantry fixtures.
internal val user: UserId = (UserId.of("user-1") as AppResult.Success).data
internal val otherUser: UserId = (UserId.of("user-2") as AppResult.Success).data
internal val stranger: UserId = (UserId.of("user-3") as AppResult.Success).data

internal fun kitchenId(raw: String): KitchenId = (KitchenId.of(raw) as AppResult.Success).data

internal fun kitchenJoinCode(raw: String): KitchenJoinCode = (KitchenJoinCode.of(raw) as AppResult.Success).data

internal fun kitchen(
    id: String = "kitchen-1",
    ownerId: UserId = user,
    memberIds: Set<UserId> = setOf(ownerId),
    joinCode: String = "code-1",
    memberDisplayNames: Map<String, String> = emptyMap(),
): Kitchen = Kitchen(kitchenId(id), ownerId, memberIds, kitchenJoinCode(joinCode), memberDisplayNames)
