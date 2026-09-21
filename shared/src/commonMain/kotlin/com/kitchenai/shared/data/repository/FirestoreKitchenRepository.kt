package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.KitchenDto
import com.kitchenai.shared.data.remote.dto.KitchenInviteDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.remote.firebase.firestoreCall
import com.kitchenai.shared.data.remote.firebase.reportingErrorsTo
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import dev.gitlive.firebase.firestore.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.transform

/**
 * [KitchenRepositoryContract] over `kitchens/{kitchenId}` and `kitchenInvites/{code}`. There is
 * no `users/{uid}` field pointing at a member's kitchen, so "my kitchen" is a query
 * (`memberIds` array-contains the uid) rather than a document read — the same query the
 * Firestore rules use to authorise it.
 *
 * Membership writes go through [FirebaseFirestore.runTransaction]: joining reads the invite and
 * the kitchen it names in the same round trip a stale, concurrently-regenerated code would
 * invalidate, and every membership change reads the current member list before writing it back.
 */
class FirestoreKitchenRepository(
    private val paths: FirestorePaths,
    private val firestore: FirebaseFirestore,
    private val ids: IdGenerator,
    private val dispatchers: DispatcherProvider,
) : KitchenRepositoryContract {
    private val errors = KeyedErrorSinks<UserId>()

    override fun observeMyKitchen(userId: UserId): Flow<Kitchen> {
        val sink = errors.of(userId)
        return paths
            .kitchens()
            .where { MEMBER_IDS contains userId.value }
            .snapshots
            .transform { snapshot -> emitOrReport(snapshot.toKitchen(), sink) }
            .reportingErrorsTo(sink)
    }

    override fun kitchenErrors(userId: UserId): Flow<AppError> = errors.of(userId).asSharedFlow()

    override suspend fun getMyKitchen(userId: UserId): AppResult<Kitchen> =
        firestoreCall(dispatchers) { paths.kitchens().where { MEMBER_IDS contains userId.value }.get() }
            .flatMap { it.toKitchen() }

    override suspend fun createKitchen(
        ownerId: UserId,
        displayName: String?,
    ): AppResult<Kitchen> {
        val kitchenId = KitchenId.of(ids.newId()).getOrElse { return AppResult.Failure(it) }
        val joinCode = KitchenJoinCode.of(ids.newId()).getOrElse { return AppResult.Failure(it) }
        val kitchen =
            Kitchen(
                id = kitchenId,
                ownerId = ownerId,
                memberIds = setOf(ownerId),
                joinCode = joinCode,
                memberDisplayNames = displayName?.let { mapOf(ownerId.value to it) }.orEmpty(),
            )
        return firestoreCall(dispatchers) {
            firestore
                .batch()
                .set(paths.kitchen(kitchenId), kitchen.toDto()) { encodeDefaults = true }
                .set(paths.kitchenInvite(joinCode), KitchenInviteDto(kitchenId.value)) { encodeDefaults = true }
                .commit()
        }.map { kitchen }
    }

    override suspend fun joinKitchen(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen> =
        firestoreCall(dispatchers) { firestore.runTransaction { joinTransaction(userId, displayName, joinCode) } }.flatMap { it }

    override suspend fun leaveKitchen(
        userId: UserId,
        kitchenId: KitchenId,
    ): AppResult<Unit> =
        firestoreCall(dispatchers) { firestore.runTransaction { removeFromKitchenTransaction(kitchenId, userId) } }.flatMap { it }

    override suspend fun removeMember(
        kitchenId: KitchenId,
        requesterId: UserId,
        memberId: UserId,
    ): AppResult<Unit> =
        firestoreCall(dispatchers) {
            firestore.runTransaction { removeMemberTransaction(kitchenId, requesterId, memberId) }
        }.flatMap { it }

    override suspend fun regenerateJoinCode(
        kitchenId: KitchenId,
        requesterId: UserId,
    ): AppResult<Kitchen> =
        firestoreCall(dispatchers) {
            firestore.runTransaction { regenerateJoinCodeTransaction(kitchenId, requesterId) }
        }.flatMap { it }

    /** A single dotted-path write: no read needed, and no other member's entry is touched. */
    override suspend fun updateMyDisplayName(
        userId: UserId,
        kitchenId: KitchenId,
        displayName: String,
    ): AppResult<Unit> =
        firestoreCall(dispatchers) {
            paths.kitchen(kitchenId).updateFields { "$MEMBER_DISPLAY_NAMES.${userId.value}" to displayName }
        }

    private suspend fun Transaction.joinTransaction(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen> {
        val inviteSnapshot = get(paths.kitchenInvite(joinCode))
        if (!inviteSnapshot.exists) return AppResult.Failure(AppError.NotFound(INVITE_RESOURCE))
        val invite =
            runCatching { inviteSnapshot.data(KitchenInviteDto.serializer()) }
                .getOrElse { return AppResult.Failure(it.toAppError()) }
        val kitchenId = KitchenId.of(invite.kitchenId.orEmpty()).getOrElse { return AppResult.Failure(it) }
        val kitchenRef = paths.kitchen(kitchenId)
        val kitchenSnapshot = get(kitchenRef)
        if (!kitchenSnapshot.exists) return AppResult.Failure(AppError.NotFound(KITCHEN_RESOURCE))
        val dto =
            runCatching { kitchenSnapshot.data(KitchenDto.serializer()) }
                .getOrElse { return AppResult.Failure(it.toAppError()) }
        val updated =
            dto.copy(
                memberIds = (dto.memberIds + userId.value).distinct(),
                memberDisplayNames = displayName?.let { dto.memberDisplayNames + (userId.value to it) } ?: dto.memberDisplayNames,
            )
        set(kitchenRef, updated) { encodeDefaults = true }
        return updated.toDomain(kitchenId.value)
    }

    private suspend fun Transaction.removeFromKitchenTransaction(
        kitchenId: KitchenId,
        userId: UserId,
    ): AppResult<Unit> {
        val kitchenRef = paths.kitchen(kitchenId)
        val snapshot = get(kitchenRef)
        if (!snapshot.exists) return AppResult.Failure(AppError.NotFound(KITCHEN_RESOURCE))
        val dto = runCatching { snapshot.data(KitchenDto.serializer()) }.getOrElse { return AppResult.Failure(it.toAppError()) }
        set(kitchenRef, dto.withoutMember(userId)) { encodeDefaults = true }
        return AppResult.Success(Unit)
    }

    private suspend fun Transaction.removeMemberTransaction(
        kitchenId: KitchenId,
        requesterId: UserId,
        memberId: UserId,
    ): AppResult<Unit> {
        val kitchenRef = paths.kitchen(kitchenId)
        val snapshot = get(kitchenRef)
        if (!snapshot.exists) return AppResult.Failure(AppError.NotFound(KITCHEN_RESOURCE))
        val dto = runCatching { snapshot.data(KitchenDto.serializer()) }.getOrElse { return AppResult.Failure(it.toAppError()) }
        if (dto.ownerId != requesterId.value) return AppResult.Failure(AppError.Unauthorized())
        set(kitchenRef, dto.withoutMember(memberId)) { encodeDefaults = true }
        return AppResult.Success(Unit)
    }

    /** Deletes the old invite in the same transaction it writes the new one, so it stops resolving immediately. */
    private suspend fun Transaction.regenerateJoinCodeTransaction(
        kitchenId: KitchenId,
        requesterId: UserId,
    ): AppResult<Kitchen> {
        val kitchenRef = paths.kitchen(kitchenId)
        val snapshot = get(kitchenRef)
        if (!snapshot.exists) return AppResult.Failure(AppError.NotFound(KITCHEN_RESOURCE))
        val dto = runCatching { snapshot.data(KitchenDto.serializer()) }.getOrElse { return AppResult.Failure(it.toAppError()) }
        if (dto.ownerId != requesterId.value) return AppResult.Failure(AppError.Unauthorized())
        val newCode = KitchenJoinCode.of(ids.newId()).getOrElse { return AppResult.Failure(it) }
        val updated = dto.copy(joinCode = newCode.value)
        set(kitchenRef, updated) { encodeDefaults = true }
        dto.joinCode.asJoinCodeOrNull()?.let { oldCode -> delete(paths.kitchenInvite(oldCode)) }
        set(paths.kitchenInvite(newCode), KitchenInviteDto(kitchenId.value)) { encodeDefaults = true }
        return updated.toDomain(kitchenId.value)
    }

    private fun KitchenDto.withoutMember(memberId: UserId): KitchenDto =
        copy(
            memberIds = memberIds - memberId.value,
            memberDisplayNames = memberDisplayNames - memberId.value,
        )

    /** A code already stored is already valid; this only guards a document written by hand. */
    private fun String?.asJoinCodeOrNull(): KitchenJoinCode? = this?.let { raw -> KitchenJoinCode.of(raw).getOrElse { null } }

    private fun QuerySnapshot.toKitchen(): AppResult<Kitchen> {
        val document = documents.firstOrNull() ?: return AppResult.Failure(AppError.NotFound(KITCHEN_RESOURCE))
        return document.decode()
    }

    private fun DocumentSnapshot.decode(): AppResult<Kitchen> =
        runCatching { data(KitchenDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )

    private suspend fun FlowCollector<Kitchen>.emitOrReport(
        decoded: AppResult<Kitchen>,
        sink: MutableSharedFlow<AppError>,
    ) = when (decoded) {
        is AppResult.Success -> emit(decoded.data)
        is AppResult.Failure -> sink.emit(decoded.error)
    }

    private companion object {
        const val MEMBER_IDS = "memberIds"
        const val MEMBER_DISPLAY_NAMES = "memberDisplayNames"

        // The collection, never the identifier: an error carries no user content.
        const val KITCHEN_RESOURCE = "kitchen"
        const val INVITE_RESOURCE = "kitchenInvite"
    }
}
