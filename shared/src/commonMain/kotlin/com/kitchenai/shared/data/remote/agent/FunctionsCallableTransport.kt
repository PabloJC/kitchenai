package com.kitchenai.shared.data.remote.agent

import com.kitchenai.shared.data.remote.agent.dto.SuggestRequestDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestResponseDto
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A model call can take a while, but it cannot take forever: without this the caller waits on
 * the platform default, a minute on Android and effectively unbounded on a stalled connection.
 *
 * Sixty rather than thirty. Thirty was chosen against a warm function and fits one comfortably,
 * but a cold start plus inference does not: the first two calls of a session on a device both
 * timed out at thirty and the third, warm, answered well inside it. A first attempt that fails
 * is worse than one that is slow, and this only bounds the wait — a warm call still returns when
 * it returns.
 */
private val CALL_TIMEOUT: Duration = 60.seconds

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
