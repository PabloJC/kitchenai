package com.kitchenai.admin.config

import kotlinx.browser.window
import kotlinx.coroutines.await

suspend fun loadFirebaseConfig(): Result<AdminFirebaseConfig> {
    val url = configUrl(window.location.hostname)
    val response = window.fetch(url).await()
    if (!response.ok) {
        return Result.failure(IllegalStateException("Could not load $url (${response.status})"))
    }
    return parseFirebaseConfig(response.text().await())
}
