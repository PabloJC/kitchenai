package com.kitchenai.shared.data.system

import com.kitchenai.shared.domain.port.TimeProvider
import kotlin.time.Clock
import kotlin.time.Instant

/** The wall clock behind [TimeProvider]. Tests bind a frozen implementation instead. */
class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}
