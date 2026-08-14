package com.kitchenai.shared.data.remote.agent

import com.kitchenai.shared.data.remote.agent.dto.SuggestRequestDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestResponseDto
import kotlinx.coroutines.CancellationException

/**
 * A transport that can fail the way the real one does. Wave 4 lost four bugs to fakes that
 * could only succeed, so this one refuses the call, is cancelled, and returns a body whose
 * shape is right and whose contents are not.
 */
internal class FakeCallableTransport(
    private val answer: Answer,
) : CallableTransport {
    var calls = 0
        private set
    var lastRequest: SuggestRequestDto? = null
        private set
    var lastName: String? = null
        private set

    override suspend fun call(
        name: String,
        request: SuggestRequestDto,
    ): SuggestResponseDto {
        calls++
        lastName = name
        lastRequest = request
        return when (answer) {
            is Answer.Body -> answer.response
            is Answer.Rejected -> throw answer.failure
            is Answer.Cancelled -> throw CancellationException("the caller went away")
        }
    }

    sealed interface Answer {
        /** Whatever the server said, usable or not. */
        data class Body(val response: SuggestResponseDto) : Answer

        /** The call never produced a body: refused, unreachable, or timed out. */
        data class Rejected(val failure: Throwable) : Answer

        /** Cancellation is not a failure to report; it must propagate. */
        data object Cancelled : Answer
    }
}
