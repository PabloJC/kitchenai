package com.kitchenai.shared.data.system

import platform.Foundation.NSUUID

internal actual fun randomUuid(): String = NSUUID().UUIDString()
