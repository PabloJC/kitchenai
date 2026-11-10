package com.kitchenai.shared.data.local

import kotlinx.browser.localStorage
import org.koin.core.scope.Scope

internal actual fun Scope.platformPreferenceStore(): PreferenceStore =
    object : PreferenceStore {
        override fun read(key: String): String? = localStorage.getItem(PREFIX + key)

        override fun write(
            key: String,
            value: String,
        ) {
            localStorage.setItem(PREFIX + key, value)
        }
    }

// Same name as the Android preferences file, so keys never collide with another app on the origin.
private const val PREFIX = "kitchenai.preferences."
