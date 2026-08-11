package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Every suspending Firestore call goes through here, which is what makes "no exception
 * crosses a boundary" checkable by grep instead of by reading each repository.
 */
suspend fun <T> firestoreCall(
    dispatchers: DispatcherProvider,
    block: suspend () -> T,
): AppResult<T> =
    withContext(dispatchers.io) {
        runCatching { block() }.fold(
            onSuccess = { AppResult.Success(it) },
            // `runCatching` also catches cancellation; `toAppError` rethrows it.
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

/** The snapshot-flow counterpart of [firestoreCall]: a listener error becomes a `Failure` emission. */
fun <T> Flow<T>.asAppResultFlow(): Flow<AppResult<T>> =
    map<T, AppResult<T>> { AppResult.Success(it) }
        .catch { emit(AppResult.Failure(it.toAppError())) }
