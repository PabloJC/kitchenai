package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.port.HealthCheckPort
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Step 1 smoke test; delete once a real feature reads from Firebase. */
class FirebaseHealthCheckAdapter(
    private val dispatchers: DispatcherProvider,
) : HealthCheckPort {
    override suspend fun projectId(): AppResult<String> =
        withContext(dispatchers.io) {
            runCatching {
                // Throws if the SDK was never initialised.
                Firebase.app

                // Forces the lazy init of the Auth module.
                Firebase.auth

                // Not `Firebase.app.options`: see [firebaseProjectId].
                firebaseProjectId()
            }.fold(
                onSuccess = { projectId ->
                    if (projectId.isNullOrBlank()) {
                        AppResult.Failure(
                            AppError.Unknown(IllegalStateException("Firebase started without a projectId")),
                        )
                    } else {
                        AppResult.Success(projectId)
                    }
                },
                onFailure = { throwable ->
                    // `runCatching` also catches cancellation; swallowing it would break
                    // structured concurrency.
                    if (throwable is CancellationException) throw throwable

                    AppResult.Failure(AppError.Unknown(throwable))
                },
            )
        }
}
