package com.kitchenai.shared.data.remote.agent

import kotlinx.coroutines.CancellationException

/**
 * A transport that can fail the way the real one does. Wave 4 lost four bugs to fakes that
 * could only succeed, so this one refuses the call, times out, returns something that is not
 * JSON, and returns JSON whose shape is right and whose contents are not.
 */
internal class FakeCallableTransport(
    private val answer: Answer,
) : CallableTransport {
    var calls = 0
        private set
    var lastPayload: String? = null
        private set
    var lastName: String? = null
        private set

    override suspend fun call(
        name: String,
        payload: String,
    ): String {
        calls++
        lastName = name
        lastPayload = payload
        return when (answer) {
            is Answer.Body -> answer.json
            is Answer.Rejected -> throw answer.failure
            is Answer.Cancelled -> throw CancellationException("the caller went away")
        }
    }

    sealed interface Answer {
        /** Whatever the server said, well formed or not. */
        data class Body(val json: String) : Answer

        /** The call never produced a body: refused, unreachable, or timed out. */
        data class Rejected(val failure: Throwable) : Answer

        /** Cancellation is not a failure to report; it must propagate. */
        data object Cancelled : Answer
    }
}
