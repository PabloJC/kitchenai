package com.kitchenai.shared.data.remote.agent

import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A model call can take a while, but it cannot take forever: without this the caller waits on
 * the platform default, which is a minute on Android and unbounded in practice on a stalled
 * connection. Thirty seconds is longer than a healthy generation and shorter than a user's
 * patience.
 */
private val CALL_TIMEOUT: Duration = 30.seconds

/**
 * The real transport. It moves `JsonElement` rather than the DTOs so the seam above it stays
 * text: what comes back is not a response until the validator says so.
 */
internal class FunctionsCallableTransport(
    private val functions: FirebaseFunctions,
) : CallableTransport {
    private val json = Json

    override suspend fun call(
        name: String,
        payload: String,
    ): String {
        val request = json.parseToJsonElement(payload)
        val result = functions.httpsCallable(name, CALL_TIMEOUT).invoke(JsonElement.serializer(), request)
        return json.encodeToString(JsonElement.serializer(), result.data(JsonElement.serializer()))
    }
}
