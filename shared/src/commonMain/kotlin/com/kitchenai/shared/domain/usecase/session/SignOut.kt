package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.port.SessionPort
import com.kitchenai.shared.domain.usecase.NoParams
import com.kitchenai.shared.domain.usecase.UseCase

/** No MVP screen calls it; the test harness and the profile screen will. */
class SignOut(
    private val session: SessionPort,
) : UseCase<NoParams, Unit> {
    override suspend fun invoke(params: NoParams): AppResult<Unit> = session.signOut()
}
