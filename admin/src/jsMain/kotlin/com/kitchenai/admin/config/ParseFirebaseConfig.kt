package com.kitchenai.admin.config

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val lenient = Json { ignoreUnknownKeys = true }

/** Hosting's init.json carries more keys than the admin uses; a missing one fails here, not at the first call. */
fun parseFirebaseConfig(json: String): Result<AdminFirebaseConfig> {
    val config =
        try {
            lenient.decodeFromString<AdminFirebaseConfig>(json)
        } catch (failure: SerializationException) {
            return Result.failure(IllegalArgumentException("The Firebase config is not valid JSON", failure))
        }
    val missing =
        listOf(
            "apiKey" to config.apiKey,
            "authDomain" to config.authDomain,
            "projectId" to config.projectId,
            "storageBucket" to config.storageBucket,
            "appId" to config.appId,
        ).filter { (_, value) -> value.isBlank() }.map { (name, _) -> name }
    return if (missing.isEmpty()) {
        Result.success(config)
    } else {
        Result.failure(IllegalArgumentException("The Firebase config has no ${missing.joinToString()}"))
    }
}
