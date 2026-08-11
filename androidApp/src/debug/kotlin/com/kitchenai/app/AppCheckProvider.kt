package com.kitchenai.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * App Check provider for debug builds.
 *
 * Prints a debug token to Logcat on first launch; register it in the console so the emulator,
 * the simulator and CI keep working. See `docs/infra.md`.
 *
 * It lives in `src/debug` rather than behind `if (BuildConfig.DEBUG)` because
 * `firebase-appcheck-debug` comes in through `debugImplementation`: this way it is absent
 * from the release APK, not merely unreachable.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory =
    DebugAppCheckProviderFactory.getInstance()
