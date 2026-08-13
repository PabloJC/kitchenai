package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.firebase.toAppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Issues [write] against the local cache and returns without waiting for the server: GitLive's
 * `set`, `delete` and `commit` only resolve once the server acknowledges them, so awaiting one
 * would leave a user who ticked a line offline watching a spinner for a write already applied.
 *
 * A failure that does arrive later is published on [errors] rather than swallowed. Being offline
 * is not one of them: Firestore replays the write when it can.
 *
 * No caller cancels the scope it launches on, deliberately: every repository owning one is a
 * singleton, and cancelling would drop a write the caller was already told had been accepted.
 */
internal fun CoroutineScope.optimistically(
    errors: MutableSharedFlow<AppError>,
    write: suspend () -> Unit,
): AppResult<Unit> {
    launch {
        // `runCatching` also catches cancellation; `toAppError` rethrows it, so a torn-down scope
        // propagates instead of arriving on `errors` as an ordinary failure.
        runCatching { write() }.onFailure { failure -> errors.emit(failure.toAppError()) }
    }
    return AppResult.Success(Unit)
}
