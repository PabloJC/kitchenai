package com.kitchenai.shared.data.remote.agent

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.testDispatchers
import com.kitchenai.shared.data.remote.firebase.FUNCTIONS_RESOURCE
import com.kitchenai.shared.data.remote.firebase.appErrorForCode
import com.kitchenai.shared.domain.agent.AgentCapability
import com.kitchenai.shared.domain.agent.AgentContext
import com.kitchenai.shared.domain.agent.PantryEntry
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CallableFunctionRecipeAgentTest {
    @Test
    fun `sends identifiers and numbers and no prose at all`() =
        runTest {
            val transport = FakeCallableTransport(FakeCallableTransport.Answer.Body(WELL_FORMED))

            agent(transport).suggest(context())

            val payload = transport.lastPayload.orEmpty()
            assertEquals("suggestRecipes", transport.lastName)
            assertTrue(payload.contains("\"capability\":\"SUGGEST_FROM_PANTRY\""))
            assertTrue(payload.contains("\"ingredientId\":\"ingredient-1\""))
            // The one thing this request must never carry. The user's own words live on the
            // device; resolving identifiers to language is the function's job.
            assertFalse(payload.contains("freeText"))
            assertFalse(payload.contains("prompt"))
        }

    @Test
    fun `maps a well formed body to recipes`() =
        runTest {
            val result = agent(FakeCallableTransport(FakeCallableTransport.Answer.Body(WELL_FORMED))).suggest(context())

            val suggestions = assertIs<AppResult.Success<*>>(result).data
            assertEquals(1, (suggestions as com.kitchenai.shared.domain.agent.AgentSuggestions).suggestions.size)
        }

    @Test
    fun `reports a body that is not JSON instead of throwing`() =
        runTest {
            val transport = FakeCallableTransport(FakeCallableTransport.Answer.Body("not json at all"))

            val result = agent(transport).suggest(context())

            assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(result).error)
        }

    @Test
    fun `reports a body missing a required key instead of guessing it`() =
        runTest {
            val transport = FakeCallableTransport(FakeCallableTransport.Answer.Body("""{"schemaVersion":1}"""))

            val result = agent(transport).suggest(context())

            assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(result).error)
        }

    @Test
    fun `ignores a key the server added after this client shipped`() =
        runTest {
            val extended = WELL_FORMED.replace("""{"schemaVersion":1""", """{"tokensUsed":42,"schemaVersion":1""")

            val result = agent(FakeCallableTransport(FakeCallableTransport.Answer.Body(extended))).suggest(context())

            assertIs<AppResult.Success<*>>(result)
        }

    /**
     * The code table only. The type dispatch in `toAppError` cannot be reached from here: on
     * Android both SDK exception types are typealiases whose static initialisers need the
     * Android runtime, which is the same reason `appErrorForCode` is keyed on a name at all.
     * That branch is one line, and it is what review caught missing for the functions SDK.
     */
    @Test
    fun `maps every status code the callable can return`() {
        val cases =
            mapOf(
                "UNAUTHENTICATED" to AppError.Unauthorized::class,
                "PERMISSION_DENIED" to AppError.Unauthorized::class,
                "UNAVAILABLE" to AppError.Network::class,
                "DEADLINE_EXCEEDED" to AppError.Network::class,
                // Retryable, but not a connection problem, and Network renders as "No connection".
                "RESOURCE_EXHAUSTED" to AppError.Unknown::class,
                "INTERNAL" to AppError.Unknown::class,
            )

        cases.forEach { (code, expected) ->
            assertEquals(expected, appErrorForCode(code, null, FUNCTIONS_RESOURCE)::class, "code $code")
        }
    }

    @Test
    fun `says the function is missing rather than blaming the document store`() {
        val error = appErrorForCode("NOT_FOUND", null, FUNCTIONS_RESOURCE)

        assertEquals(AppError.NotFound(FUNCTIONS_RESOURCE), error)
    }

    @Test
    fun `does not let a rejected call escape as an exception`() =
        runTest {
            val refused = FakeCallableTransport.Answer.Rejected(IllegalStateException("refused"))
            val transport = FakeCallableTransport(refused)

            val result = agent(transport).suggest(context())

            assertIs<AppResult.Failure>(result)
        }

    @Test
    fun `lets cancellation through rather than reporting it as a failure`() =
        runTest {
            val transport = FakeCallableTransport(FakeCallableTransport.Answer.Cancelled)

            assertFailsWith<CancellationException> { agent(transport).suggest(context()) }
        }

    private fun agent(transport: FakeCallableTransport): CallableFunctionRecipeAgent {
        var counter = 0
        return CallableFunctionRecipeAgent(
            id = AgentId.of("agent-1").let { (it as AppResult.Success).data },
            capabilities = setOf(AgentCapability.SUGGEST_FROM_PANTRY),
            transport = transport,
            validator = SuggestionValidator(newRecipeId = { "recipe-${counter++}" }, now = { NOW }),
            ids = { "request-1" },
            dispatchers = testDispatchers(UnconfinedTestDispatcher()),
        )
    }

    private fun context(): AgentContext =
        AgentContext(
            languageTags = listOf("en"),
            servings = 2,
            constraints = listOf(DietaryConstraint(term("taxonomy-1", "term-1"), ConstraintStrength.EXCLUDE)),
            preferences = listOf(term("taxonomy-2", "term-2")),
            avoidedIngredients = listOf(id("ingredient-9")),
            pantry = listOf(PantryEntry(id("ingredient-1"), Quantity(2.0, term("taxonomy-3", "term-3")), true)),
            options = SuggestionOptions(maxResults = 5),
        )

    private fun term(
        taxonomy: String,
        name: String,
    ): TermRef =
        TermRef(
            (TaxonomyId.of(taxonomy) as AppResult.Success).data,
            (TermId.of(name) as AppResult.Success).data,
        )

    private fun id(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

    private companion object {
        val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000)

        val WELL_FORMED =
            """
            {"schemaVersion":1,"agentId":"agent-1","modelId":"model-1","suggestions":[
              {"title":"Title","summary":"Summary","servings":2,"totalMinutes":30,
               "ingredients":[{"ingredientId":"ingredient-1","amount":2.0}],
               "steps":["Step"],"tags":[]}]}
            """.trimIndent()
    }
}
