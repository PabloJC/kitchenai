package com.kitchenai.ui.platform

import android.content.res.Resources

/** The system list, not the app's: the app ships no translations to be filtered against yet. */
actual fun platformLanguageTags(): List<String> {
    val locales = Resources.getSystem().configuration.locales
    return (0 until locales.size()).map { index -> locales[index].toLanguageTag() }
}
