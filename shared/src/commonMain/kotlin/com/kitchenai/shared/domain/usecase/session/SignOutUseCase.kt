package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.usecase.NoParams
import com.kitchenai.shared.domain.usecase.UseCase

/** No MVP screen calls it; the test harness and the profile screen will. */
class SignOutUseCase(
    private val session: SessionRepositoryContract,
) : UseCase<NoParams, Unit> {
    override suspend fun invoke(params: NoParams): AppResult<Unit> = session.signOut()
}
