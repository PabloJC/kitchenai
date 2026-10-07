package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge

/** The one kitchen a screen opens into. Every screen ViewModel derives its `KitchenId` from it. */
val defaultKitchenId: KitchenId = (KitchenId.of("kitchen-1") as AppResult.Success).data

/**
 * A kitchen already exists by the time a screen mounts — the session gate ensures one — so this
 * defaults to one rather than to none, unlike [FakeShoppingListPort]'s default list.
 */
class FakeKitchenPort(
    initial: Kitchen? = kitchen(),
    var readError: AppError? = null,
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
    val joinedAs = mutableListOf<String?>()
    val joinedLeaving = mutableListOf<KitchenId?>()
    val createdAs = mutableListOf<String?>()
    var createCount = 0
        private set

    /** Fails the next [createKitchen] once, or holds it open after it started so a test can interleave a caller. */
    var createError: AppError? = null
    var createGate: CompletableDeferred<Unit>? = null
    var leaveCount = 0
        private set
    val removedMembers = mutableListOf<UserId>()
    var regenerateCount = 0
        private set

    override fun observeMyKitchen(userId: UserId): Flow<Kitchen> =
        if (readError != null || state.value == null) emptyFlow() else state.filterNotNull()

    // What a listener reports when the viewer's kitchen vanishes under it, as a leave or a removal does.
    private val reportedNotFound = MutableSharedFlow<AppError>()

    override fun kitchenErrors(userId: UserId): Flow<AppError> =
        merge(readError?.let { flowOf(it) } ?: emptyFlow(), reportedNotFound)

    override suspend fun getMyKitchen(userId: UserId): AppResult<Kitchen> =
        readError?.let { AppResult.Failure(it) }
            ?: state.value?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound("kitchen"))

    override suspend fun createKitchen(
        ownerId: UserId,
        displayName: String?,
    ): AppResult<Kitchen> {
        createCount++
        createdAs += displayName
        createGate?.await()
        createError?.let {
            createError = null
            return AppResult.Failure(it)
        }
        val names = displayName?.let { mapOf(ownerId.value to it) }.orEmpty()
        val id = kitchenIdOf("kitchen-created-$createCount")
        val created = kitchen(id = id, ownerId = ownerId, memberDisplayNames = names)
        state.value = created
        return AppResult.Success(created)
    }

    override suspend fun joinKitchen(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
        leaving: KitchenId?,
    ): AppResult<Kitchen> {
        joinCalls += joinCode
        joinedAs += displayName
        joinedLeaving += leaving
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

    /** The viewer is no longer in any kitchen: the listener goes quiet and then reports it, like the real query. */
    suspend fun dropKitchen() {
        state.value = null
        reportNotFound()
    }

    suspend fun reportNotFound() = report(AppError.NotFound("kitchen"))

    suspend fun report(error: AppError) = reportedNotFound.emit(error)

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

private fun kitchenIdOf(raw: String): KitchenId = (KitchenId.of(raw) as AppResult.Success).data
