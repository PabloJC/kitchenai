package com.kitchenai.shared.data.remote.agent

import com.kitchenai.shared.data.remote.agent.dto.SuggestRequestDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestResponseDto
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A model call can take a while, but it cannot take forever: without this the caller waits on
 * the platform default, a minute on Android and effectively unbounded on a stalled connection.
 */
private val CALL_TIMEOUT: Duration = 30.seconds

/**
 * The real transport. Encoding is the SDK's: it serialises through its own encoder, so the DTOs
 * travel as the object the callable expects rather than as a string containing one.
 */
internal class FunctionsCallableTransport(
    private val functions: FirebaseFunctions,
) : CallableTransport {
    override suspend fun call(
        name: String,
        request: SuggestRequestDto,
    ): SuggestResponseDto =
        functions
            .httpsCallable(name, CALL_TIMEOUT)
            .invoke(SuggestRequestDto.serializer(), request)
            .data(SuggestResponseDto.serializer())
}
