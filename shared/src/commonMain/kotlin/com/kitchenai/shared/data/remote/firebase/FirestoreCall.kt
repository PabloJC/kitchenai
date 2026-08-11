package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
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

/**
 * The snapshot-flow counterpart of [firestoreCall]. The stream carries data only, so a listener
 * error ends it and is published on the port's matching error stream instead of riding an
 * emission. One shared flow per keyed listener, never one per port.
 */
fun <T> Flow<T>.reportingErrorsTo(errors: MutableSharedFlow<AppError>): Flow<T> = catch { errors.emit(it.toAppError()) }
