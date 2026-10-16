package com.kitchenai.shared.data.local

import org.koin.core.scope.Scope

/** The platform's own small key-value store: SharedPreferences on Android, NSUserDefaults on iOS. */
interface PreferenceStore {
    fun read(key: String): String?

    fun write(
        key: String,
        value: String,
    )
}

/** Resolved from the Koin [Scope] like the database builder, so the platform half stays in its own source set. */
internal expect fun Scope.platformPreferenceStore(): PreferenceStore
