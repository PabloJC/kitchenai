package com.kitchenai.shared.data.local

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Every suspending call to a local data source goes through here, mirroring `firestoreCall` for
 * Firestore: what makes "no exception crosses a boundary" checkable by grep instead of by
 * reading each repository. There is no local equivalent of Firestore's error codes — a local
 * backend fails or it does not — so every failure but a cancellation becomes [AppError.Unknown].
 */
suspend fun <T> localCall(
    dispatchers: DispatcherProvider,
    block: suspend () -> T,
): AppResult<T> =
    withContext(dispatchers.io) {
        runCatching { block() }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                AppResult.Failure(AppError.Unknown(failure))
            },
        )
    }
