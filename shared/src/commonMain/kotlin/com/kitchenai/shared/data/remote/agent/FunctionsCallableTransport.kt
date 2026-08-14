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
 * Thirty was chosen against a warm function and a cold start plus inference does not fit it:
 * the first two calls of a session on a device both timed out at thirty and the third, warm,
 * answered well inside it.
 *
 * This must stay **above** the function's own `timeoutSeconds`, and the margin is the point. The
 * function gives up first and answers with an error the client is still listening for; a client
 * that gave up first would abandon work still running and still being paid for, and report a
 * timeout for a call that was about to succeed. Equal values are the worst case: both give up at
 * once and which one wins is a race.
 */
private val CALL_TIMEOUT: Duration = 90.seconds

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
