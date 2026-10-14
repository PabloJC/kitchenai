package com.kitchenai.shared.domain.model

import kotlin.jvm.JvmInline

/** The unhashed nonce whose SHA-256 went to Apple; Firebase checks the token against it, once. */
@JvmInline
value class AppleRawNonce(val value: String)
