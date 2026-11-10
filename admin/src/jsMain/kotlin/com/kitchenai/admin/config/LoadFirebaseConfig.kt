package com.kitchenai.admin.config

import com.kitchenai.admin.core.catchingFailures
import kotlinx.browser.window
import kotlinx.coroutines.await

suspend fun loadFirebaseConfig(): Result<AdminFirebaseConfig> {
    val url = configUrl(window.location.hostname)
    val text =
        catchingFailures {
            val response = window.fetch(url).await()
            check(response.ok) { "status ${response.status}" }
            response.text().await()
        }
    return text.fold(
        onSuccess = ::parseFirebaseConfig,
        onFailure = { failure -> Result.failure(IllegalStateException("Could not load $url: ${failure.message}")) },
    )
}
