package com.kitchenai.shared.domain.model

import kotlin.jvm.JvmInline

/** The opaque ID token from Google's sign-in flow. No email, no name: those stay on the platform side. */
@JvmInline
value class GoogleIdToken(val value: String)
