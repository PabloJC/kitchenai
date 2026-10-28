package com.kitchenai.ui.navigation

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IdeasRequestsTest {
    @Test
    fun `a request made before Ideas listens waits for it and arrives once`() =
        runTest {
            val requests = IdeasRequests()

            requests.requestUseExpiring()
            requests.requestUseExpiring()

            requests.useExpiring.test {
                awaitItem()
                expectNoEvents()
            }
        }
}
