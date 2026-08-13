package com.kitchenai.shared.data.remote.agent

/**
 * The one call this feature makes, behind an interface so the adapter can be tested without a
 * Firebase project — and so a fake can do what a real callable does: time out, reject the call,
 * and hand back a body that parses but says nothing usable.
 *
 * It speaks JSON strings rather than objects because that is the boundary where trust ends:
 * whatever comes back is text until the validator has looked at it.
 */
internal fun interface CallableTransport {
    /** Throws on transport failure; the adapter is what turns that into an [com.kitchenai.shared.core.AppError]. */
    suspend fun call(
        name: String,
        payload: String,
    ): String
}
