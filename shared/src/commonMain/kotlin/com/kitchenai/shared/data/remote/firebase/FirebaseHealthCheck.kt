package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.auth

/**
 * Comprueba que los SDK de Firebase están inicializados en ambas plataformas
 * y devuelve el `projectId` con el que arrancaron.
 *
 * Es el smoke test del Paso 1; se borrará cuando exista la primera feature real.
 */
class FirebaseHealthCheck {
    operator fun invoke(): AppResult<String> =
        runCatching {
            // Firebase.app lanza si el SDK no se inicializó:
            // en Android lo hace el ContentProvider de google-services,
            // en iOS la llamada a FirebaseApp.configure() de iOSApp.swift.
            val projectId = Firebase.app.options.projectId

            // Fuerza la inicialización perezosa del módulo de Auth.
            Firebase.auth

            projectId
        }.fold(
            onSuccess = { projectId ->
                if (projectId.isNullOrBlank()) {
                    AppResult.Failure(
                        AppError.Unknown(IllegalStateException("Firebase arrancó sin projectId")),
                    )
                } else {
                    AppResult.Success(projectId)
                }
            },
            onFailure = { AppResult.Failure(AppError.Unknown(it)) },
        )
}
