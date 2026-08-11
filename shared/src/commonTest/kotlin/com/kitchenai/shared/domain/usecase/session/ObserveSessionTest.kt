package com.kitchenai.shared.domain.usecase.session

import app.cash.turbine.test
import com.kitchenai.shared.domain.model.Session
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveSessionTest {
    @Test
    fun `emits the sessions the port publishes in order`() =
        runTest {
            val port = FakeSessionPort(Session.SignedOut)

            ObserveSession(port)().test {
                assertEquals(Session.SignedOut, awaitItem())

                port.emit(anonymousUser)
                assertEquals(anonymousUser, awaitItem())

                port.emit(Session.SignedOut)
                assertEquals(Session.SignedOut, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
