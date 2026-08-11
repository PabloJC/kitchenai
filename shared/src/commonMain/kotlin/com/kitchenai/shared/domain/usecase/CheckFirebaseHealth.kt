package com.kitchenai.shared.domain.usecase

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.port.HealthCheckPort

/** Returns the `projectId` Firebase started with. The port's failure is passed through as it is. */
class CheckFirebaseHealth(
    private val healthCheck: HealthCheckPort,
) : UseCase<NoParams, String> {
    override suspend fun invoke(params: NoParams): AppResult<String> = healthCheck.projectId()
}
