package com.kitchenai.shared.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SingleFlightTest {
    private val flights = SingleFlight<String, String>()
    private var calls = 0

    @Test
    fun `callers of one key in flight share one call`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val answers =
                List(3) {
                    async {
                        flights.run("key") {
                            calls++
                            gate.await()
                            "url"
                        }
                    }
                }
            runCurrent()
            gate.complete(Unit)

            assertEquals(listOf("url", "url", "url"), answers.awaitAll())
            assertEquals(1, calls)
        }

    @Test
    fun `different keys run on their own`() =
        runTest {
            listOf("a", "b").map { key ->
                async {
                    flights.run(key) {
                        calls++
                        key
                    }
                }
            }.awaitAll()

            assertEquals(2, calls)
        }

    @Test
    fun `a finished key runs again for a later caller`() =
        runTest {
            flights.run("key") {
                calls++
                "url"
            }
            flights.run("key") {
                calls++
                "url"
            }

            assertEquals(2, calls)
        }

    @Test
    fun `a waiter takes over when the owner is cancelled`() =
        runTest {
            val owner = async { flights.run("key") { CompletableDeferred<String>().await() } }
            runCurrent()
            val waiter =
                async {
                    flights.run("key") {
                        calls++
                        "url"
                    }
                }
            runCurrent()
            owner.cancel()

            assertEquals("url", waiter.await())
            assertEquals(1, calls)
        }
}
