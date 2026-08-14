package com.kitchenai.shared.data.remote.agent

import com.kitchenai.shared.data.remote.agent.dto.SuggestRequestDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestResponseDto

/**
 * The one call this feature makes, behind an interface so the adapter can be tested without a
 * Firebase project — and so a fake can do what a real callable does: reject the call, time out,
 * and hand back a body whose shape is right and whose contents are not.
 *
 * It speaks DTOs rather than JSON text because the SDK owns the encoding: its callable uses its
 * own encoder, and handing it a `JsonElement` fails at runtime with a message about formats.
 */
internal fun interface CallableTransport {
    /** Throws on transport failure; the adapter turns that into an [com.kitchenai.shared.core.AppError]. */
    suspend fun call(
        name: String,
        request: SuggestRequestDto,
    ): SuggestResponseDto
}
