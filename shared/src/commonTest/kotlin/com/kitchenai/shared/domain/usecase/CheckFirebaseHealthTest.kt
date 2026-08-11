package com.kitchenai.shared.domain.usecase

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.port.HealthCheckPort
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CheckFirebaseHealthTest {
    @Test
    fun `returns the projectId the port answers with`() =
        runTest {
            val useCase = CheckFirebaseHealth(HealthCheckPort { AppResult.Success("test-project") })

            val result = useCase(NoParams)

            assertEquals(AppResult.Success("test-project"), result)
        }

    @Test
    fun `propagates the port's Failure without wrapping it in another error`() =
        runTest {
            val error = AppError.Unknown(IllegalStateException("Firebase started without a projectId"))
            val useCase = CheckFirebaseHealth(HealthCheckPort { AppResult.Failure(error) })

            val result = useCase(NoParams)

            // assertSame: the error must arrive as it was, not an equivalent copy.
            assertTrue(result is AppResult.Failure)
            assertSame(error, result.error)
        }

    @Test
    fun `does not switch dispatchers- that is the adapter's job`() =
        runTest {
            val useCase = CheckFirebaseHealth(HealthCheckPort { AppResult.Success("test-project") })

            // UNDISPATCHED runs inline: a dispatcher hop would suspend and leave the job
            // incomplete on return.
            val job = launch(start = CoroutineStart.UNDISPATCHED) { useCase(NoParams) }

            assertTrue(job.isCompleted)
        }
}
