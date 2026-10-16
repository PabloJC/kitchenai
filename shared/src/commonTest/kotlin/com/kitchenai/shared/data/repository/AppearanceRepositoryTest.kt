package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.testDispatchers
import com.kitchenai.shared.data.local.AppearanceLocalDataSource
import com.kitchenai.shared.data.local.PreferenceStore
import com.kitchenai.shared.domain.model.Appearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppearanceRepositoryTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `nothing stored follows the system`() =
        runTest(dispatcher) {
            assertEquals(Appearance.SYSTEM, repository(MapStore()).observeAppearance().first())
        }

    @Test
    fun `a value no build of this app knows follows the system rather than failing`() =
        runTest(dispatcher) {
            assertEquals(Appearance.SYSTEM, repository(MapStore("appearance" to "SEPIA")).observeAppearance().first())
        }

    @Test
    fun `a saved choice is stored and seen by the next read`() =
        runTest(dispatcher) {
            val store = MapStore()
            val repository = repository(store)

            val result = repository.saveAppearance(Appearance.DARK)

            assertTrue(result is AppResult.Success)
            assertEquals("DARK", store.values["appearance"])
            assertEquals(Appearance.DARK, repository.observeAppearance().first())
            // A fresh read of the same store, as the next launch would make.
            assertEquals(Appearance.DARK, repository(store).observeAppearance().first())
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

            assertTrue(repository(broken).saveAppearance(Appearance.LIGHT) is AppResult.Failure)
        }

    private fun repository(store: PreferenceStore): AppearanceRepository =
        AppearanceRepository(AppearanceLocalDataSource(store), testDispatchers(dispatcher))
}

private class MapStore(vararg initial: Pair<String, String>) : PreferenceStore {
    val values = mutableMapOf(*initial)

    override fun read(key: String): String? = values[key]

    override fun write(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
