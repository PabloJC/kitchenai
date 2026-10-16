package com.kitchenai.shared.data.local

import android.content.Context
import org.koin.core.scope.Scope

internal actual fun Scope.platformPreferenceStore(): PreferenceStore {
    val preferences = get<Context>().applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    return object : PreferenceStore {
        override fun read(key: String): String? = preferences.getString(key, null)

        override fun write(
            key: String,
            value: String,
        ) {
            preferences.edit().putString(key, value).apply()
        }
    }
}

private const val FILE_NAME = "kitchenai.preferences"
