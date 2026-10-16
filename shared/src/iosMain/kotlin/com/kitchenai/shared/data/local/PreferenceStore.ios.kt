package com.kitchenai.shared.data.local

import org.koin.core.scope.Scope
import platform.Foundation.NSUserDefaults

internal actual fun Scope.platformPreferenceStore(): PreferenceStore {
    val defaults = NSUserDefaults.standardUserDefaults
    return object : PreferenceStore {
        override fun read(key: String): String? = defaults.stringForKey(key)

        override fun write(
            key: String,
            value: String,
        ) {
            defaults.setObject(value, forKey = key)
        }
    }
}
