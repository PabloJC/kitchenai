package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.AccountRepositoryContract
import com.kitchenai.shared.domain.usecase.NoParams
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeleteAccountUseCaseTest {
    @Test
    fun `deletes the account on the server and then signs out locally`() =
        runTest {
            val account = FakeAccountPort(AppResult.Success(Unit))
            val session = FakeSessionPort(anonymousUser.copy(isAnonymous = false))

            val result = DeleteAccountUseCase(account, session)(NoParams)

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(1, account.calls)
            assertEquals(Session.SignedOut, session.observeSession().first())
        }

    @Test
    fun `a refused deletion keeps the session signed in and reports why`() =
        runTest {
            val error = AppError.Network()
            val signedIn = anonymousUser.copy(isAnonymous = false)
            val session = FakeSessionPort(signedIn)

            val result = DeleteAccountUseCase(FakeAccountPort(AppResult.Failure(error)), session)(NoParams)

            assertTrue(result is AppResult.Failure)
            assertSame(error, result.error)
            assertEquals(signedIn, session.observeSession().first())
        }

    @Test
    fun `a failed local sign-out is reported and a retry completes it`() =
        runTest {
            val error = AppError.Unknown(IllegalStateException("sign-out"))
            val account = FakeAccountPort(AppResult.Success(Unit))
            val session =
                FakeSessionPort(anonymousUser.copy(isAnonymous = false)).apply {
                    signOutResult = AppResult.Failure(error)
                }
            val deleteAccount = DeleteAccountUseCase(account, session)

            val first = deleteAccount(NoParams)
            assertTrue(first is AppResult.Failure)
            assertSame(error, first.error)

            // The server steps are idempotent, so asking again repeats them and then signs out.
            session.signOutResult = AppResult.Success(Unit)
            assertEquals(AppResult.Success(Unit), deleteAccount(NoParams))
            assertEquals(2, account.calls)
            assertEquals(Session.SignedOut, session.observeSession().first())
        }
}

private class FakeAccountPort(
    private val result: AppResult<Unit>,
) : AccountRepositoryContract {
    var calls = 0

    override suspend fun deleteAccount(): AppResult<Unit> {
        calls++
        return result
    }
}
