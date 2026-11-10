package com.kitchenai.admin.core

import com.kitchenai.shared.core.AppError

/** An admin reads the cause, not a friendly sentence: it is the person who can fix it. */
fun AppError.messageFor(what: String): String = "Could not read $what: ${cause?.message ?: this::class.simpleName}"
