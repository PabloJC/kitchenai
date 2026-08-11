package com.kitchenai.shared.domain.port

/** Identifier generation as a port: the source of randomness is platform-specific, and tests need it predictable. */
fun interface IdGenerator {
    fun newId(): String
}
