package com.kitchenai.shared.core

/** Runtime platform identity. Diagnostics only. */
interface Platform {
    val name: String
}

expect fun currentPlatform(): Platform
