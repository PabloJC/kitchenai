package com.kitchenai.shared.domain.model

import kotlin.jvm.JvmInline

/** Google's access token, handed to Firebase alongside the ID token on iOS, whose credential factory requires both. */
@JvmInline
value class GoogleAccessToken(val value: String)
