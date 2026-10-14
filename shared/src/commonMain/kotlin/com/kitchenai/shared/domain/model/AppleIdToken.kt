package com.kitchenai.shared.domain.model

import kotlin.jvm.JvmInline

/** The opaque identity token from Sign in with Apple. No email, no name: those stay on the platform side. */
@JvmInline
value class AppleIdToken(val value: String)
