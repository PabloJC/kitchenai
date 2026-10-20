package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.testDispatchers
import com.kitchenai.shared.data.local.OnboardingLocalDataSource
import com.kitchenai.shared.data.local.PreferenceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingRepositoryTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `nothing stored has not been through the first run`() =
        runTest(dispatcher) {
            assertFalse(repository(FlagStore()).observeCompleted().first())
        }

    @Test
    fun `a value this build did not write reads as not done`() =
        runTest(dispatcher) {
            assertFalse(repository(FlagStore("onboarding.completed" to "yes")).observeCompleted().first())
        }

    @Test
    fun `completing is stored and survives the next launch`() =
        runTest(dispatcher) {
            val store = FlagStore()

            assertTrue(repository(store).markCompleted() is AppResult.Success)

            assertTrue(repository(store).observeCompleted().first())
        }

    @Test
    fun `a store that throws on write is reported rather than crashing`() =
        runTest(dispatcher) {
            val broken =
                object : PreferenceStore {
                    override fun read(key: String): String? = null

                    override fun write(
                        key: String,
                        value: String,
                    ): Unit = error("disk full")
                }

            assertTrue(repository(broken).markCompleted() is AppResult.Failure)
        }

    private fun repository(store: PreferenceStore): OnboardingRepository =
        OnboardingRepository(OnboardingLocalDataSource(store), testDispatchers(dispatcher))
}

private class FlagStore(vararg initial: Pair<String, String>) : PreferenceStore {
    private val values = mutableMapOf(*initial)

    override fun read(key: String): String? = values[key]

    override fun write(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
