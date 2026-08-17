package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import kotlinx.coroutines.flow.Flow

/**
 * The session stream. It returns a [Flow] and therefore cannot implement the suspend
 * `UseCase<P, R>`; presentation still depends on a use case rather than on the port.
 */
class ObserveSessionUseCase(
    private val session: SessionRepositoryContract,
) {
    operator fun invoke(): Flow<Session> = session.observeSession()
}
