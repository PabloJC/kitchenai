package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult

/** The seam: `domain` declares what it needs from Firebase, `data` provides it. */
fun interface HealthCheckPort {
    suspend fun projectId(): AppResult<String>
}
