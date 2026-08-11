package com.kitchenai.shared.data.system

import com.kitchenai.shared.domain.port.IdGenerator

/**
 * Client-generated identifiers: the shopping list must create documents while offline, so the
 * id cannot come from the server.
 */
class UuidIdGenerator : IdGenerator {
    override fun newId(): String = randomUuid()
}

internal expect fun randomUuid(): String
