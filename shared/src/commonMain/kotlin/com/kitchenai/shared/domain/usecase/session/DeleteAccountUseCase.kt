package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.port.AccountRepositoryContract
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.usecase.NoParams
import com.kitchenai.shared.domain.usecase.UseCase

/**
 * Deletes the account on the server, then signs out locally: the Auth user is already gone, and
 * signing out is what makes the session start again as a fresh guest instead of a dangling uid.
 */
class DeleteAccountUseCase(
    private val account: AccountRepositoryContract,
    private val session: SessionRepositoryContract,
) : UseCase<NoParams, Unit> {
    override suspend fun invoke(params: NoParams): AppResult<Unit> =
        when (val deleted = account.deleteAccount()) {
            is AppResult.Failure -> deleted
            is AppResult.Success -> session.signOut()
        }
}
