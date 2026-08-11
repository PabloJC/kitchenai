package com.kitchenai.shared.data.system

import java.util.UUID

internal actual fun randomUuid(): String = UUID.randomUUID().toString()
