package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.usecase.NoParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EnsureSessionTest {
    @Test
    fun `signs in anonymously when there is no session`() =
        runTest {
            val port = FakeSessionPort(Session.SignedOut)

            val result = EnsureSession(port)(NoParams)

            assertEquals(AppResult.Success(anonymousUser), result)
            assertEquals(1, port.signInCalls)
        }

    @Test
    fun `does not sign in when a session already exists`() =
        runTest {
            val port = FakeSessionPort(anonymousUser)

            val result = EnsureSession(port)(NoParams)

            assertEquals(AppResult.Success(anonymousUser), result)
            assertEquals(0, port.signInCalls)
        }

    @Test
    fun `is idempotent - a second call reuses the account the first one created`() =
        runTest {
            val port = FakeSessionPort(Session.SignedOut)
            val useCase = EnsureSession(port)

            useCase(NoParams)
            val second = useCase(NoParams)

            assertEquals(AppResult.Success(anonymousUser), second)
            assertEquals(1, port.signInCalls)
        }

    @Test
    fun `propagates the port Failure without wrapping it in another error`() =
        runTest {
            val error = AppError.Network()
            val port = FakeSessionPort(Session.SignedOut).apply { signInResult = AppResult.Failure(error) }

            val result = EnsureSession(port)(NoParams)

            assertTrue(result is AppResult.Failure)
            assertSame(error, result.error)
        }
}

internal val anonymousUser =
    Session.SignedIn(
        userId = UserId.of("test-user").getOrElse { error("the fixture id must be valid") },
        isAnonymous = true,
    )

/** Stateful on purpose: idempotence can only be proven if signing in changes what is observed. */
internal class FakeSessionPort(
    initial: Session,
) : SessionRepositoryContract {
    private val state = MutableStateFlow(initial)

    var signInResult: AppResult<Session.SignedIn> = AppResult.Success(anonymousUser)
    var signOutResult: AppResult<Unit> = AppResult.Success(Unit)
    var signInCalls: Int = 0
        private set

    override fun observeSession(): Flow<Session> = state

    override suspend fun signInAnonymously(): AppResult<Session.SignedIn> {
        signInCalls++
        val result = signInResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun signOut(): AppResult<Unit> {
        val result = signOutResult
        if (result is AppResult.Success) state.value = Session.SignedOut
        return result
    }

    fun emit(session: Session) {
        state.value = session
    }
}
