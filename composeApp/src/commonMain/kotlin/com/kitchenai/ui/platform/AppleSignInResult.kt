package com.kitchenai.ui.platform

import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce

/** What Apple's sheet hands back. [displayName] only arrives on an Apple ID's first authorisation. */
data class AppleSignInResult(
    val token: AppleIdToken,
    val rawNonce: AppleRawNonce,
    val displayName: String?,
)
