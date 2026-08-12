package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.UserProfileDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.remote.firebase.firestoreCall
import com.kitchenai.shared.data.remote.firebase.reportingErrorsTo
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfilePort
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.transform

/**
 * [UserProfilePort] over the `users/{uid}` document: a snapshot listener to read, so the screen
 * still renders from the offline cache, and a merging write to save.
 */
class FirestoreUserProfileRepository(
    private val paths: FirestorePaths,
    private val dispatchers: DispatcherProvider,
) : UserProfilePort {
    private val errors = KeyedErrorSinks<UserId>()

    override fun observeProfile(userId: UserId): Flow<UserProfile> {
        val sink = errors.of(userId)
        return paths
            .user(userId)
            .snapshots
            .transform { snapshot -> emitOrReport(snapshot.toProfile(), sink) }
            .reportingErrorsTo(sink)
    }

    override fun profileErrors(userId: UserId): Flow<AppError> = errors.of(userId).asSharedFlow()

    override suspend fun save(profile: UserProfile): AppResult<Unit> =
        firestoreCall(dispatchers) {
            // merge: the document also holds fields this client does not model yet.
            paths.user(profile.userId).set(profile.toDto(), merge = true) { encodeDefaults = true }
        }

    /**
     * A missing document reports [AppError.NotFound] and emits nothing. Creating the default
     * profile is the app shell's job; inventing one here would be a write on a read.
     */
    private fun DocumentSnapshot.toProfile(): AppResult<UserProfile> =
        if (!exists) {
            AppResult.Failure(AppError.NotFound(PROFILE_RESOURCE))
        } else {
            runCatching { data(UserProfileDto.serializer()) }.fold(
                onSuccess = { dto -> dto.toDomain(id) },
                onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
            )
        }

    private suspend fun FlowCollector<UserProfile>.emitOrReport(
        decoded: AppResult<UserProfile>,
        sink: MutableSharedFlow<AppError>,
    ) = when (decoded) {
        is AppResult.Success -> emit(decoded.data)
        is AppResult.Failure -> sink.emit(decoded.error)
    }

    private companion object {
        // The collection, never the identifier: an error carries no user content.
        const val PROFILE_RESOURCE = "profile"
    }
}
