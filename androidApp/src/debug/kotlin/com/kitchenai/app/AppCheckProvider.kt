package com.kitchenai.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Proveedor de App Check para builds de debug.
 *
 * Imprime en Logcat un token de depuración en el primer arranque
 * (`Enter this debug secret into the allow list...`). Hay que registrarlo en la
 * consola —App Check → app → Administrar tokens de depuración— para que emulador,
 * simulador y CI sigan funcionando. Ver `docs/infra.md`.
 *
 * Vive en `src/debug` y no detrás de un `if (BuildConfig.DEBUG)` porque
 * `firebase-appcheck-debug` entra por `debugImplementation`: así el proveedor no
 * está ni siquiera presente en el APK de release.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory =
    DebugAppCheckProviderFactory.getInstance()
