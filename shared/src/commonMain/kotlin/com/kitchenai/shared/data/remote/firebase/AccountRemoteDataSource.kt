package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlin.time.Duration.Companion.seconds

/** The `deleteAccount` callable; it can walk several kitchens, so it gets more than the SDK's default wait. */
private val CALL_TIMEOUT = 90.seconds

private const val DELETE_ACCOUNT = "deleteAccount"

/** Wraps the one callable that deletes an account; it takes no payload and its answer is not read. */
class AccountRemoteDataSource(
    private val functions: FirebaseFunctions,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun deleteAccount(): AppResult<Unit> =
        firestoreCall(dispatchers) {
            functions.httpsCallable(DELETE_ACCOUNT, CALL_TIMEOUT).invoke()
            Unit
        }
}
