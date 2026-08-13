package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.recipe.ingredientId
import com.kitchenai.shared.domain.usecase.recipe.recipe
import com.kitchenai.shared.domain.usecase.recipe.recipeId
import com.kitchenai.shared.domain.usecase.recipe.recipeIngredient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DefaultAgentOrchestratorTest {
    private val now = Instant.fromEpochSeconds(1_000_000)
    private val unit = termRef("term-1")
    private val options = SuggestionOptions()

    @Test
    fun `a failing agent falls through to the next candidate`() =
        runTest {
            val failing = FakeRecipeAgent("agent-1", AppResult.Failure(AppError.Network()))
            val answering = FakeRecipeAgent("agent-2", agentAnswer("agent-2", listOf(recipe())))

            val result = orchestrator(failing, answering).suggest(profile(), emptyList(), options)

            assertEquals(1, result.unwrap().size)
            assertEquals(1, answering.calls)
        }

    @Test
    fun `an unauthorized answer aborts the walk instead of fanning out`() =
        runTest {
            val rejected = FakeRecipeAgent("agent-1", AppResult.Failure(AppError.Unauthorized()))
            val answering = FakeRecipeAgent("agent-2", agentAnswer("agent-2", listOf(recipe())))

            val result = orchestrator(rejected, answering).suggest(profile(), emptyList(), options)

            assertTrue((result as AppResult.Failure).error is AppError.Unauthorized)
            assertEquals(0, answering.calls)
        }

    @Test
    fun `an empty registry is reported as not found`() =
        runTest {
            val result = orchestrator().suggest(profile(), emptyList(), options)

            assertEquals(AppError.NotFound("agent"), (result as AppResult.Failure).error)
        }

    @Test
    fun `an agent without the capability is never a candidate`() =
        runTest {
            val other =
                FakeRecipeAgent(
                    "agent-1",
                    agentAnswer("agent-1", listOf(recipe())),
                    setOf(AgentCapability.PLAN_WEEK),
                )

            val result = orchestrator(other).suggest(profile(), emptyList(), options)

            assertEquals(AppError.NotFound("agent"), (result as AppResult.Failure).error)
            assertEquals(0, other.calls)
        }

    @Test
    fun `a suggestion tagged with an excluded term is dropped and an avoided one is not`() =
        runTest {
            val excluded = termRef("term-2")
            val avoided = termRef("term-3")
            val stored =
                profile(
                    constraints =
                        listOf(
                            DietaryConstraint(excluded, ConstraintStrength.EXCLUDE),
                            DietaryConstraint(avoided, ConstraintStrength.AVOID),
                        ),
                )
            val answer =
                agentAnswer(
                    "agent-1",
                    listOf(
                        recipe(id = "recipe-1").copy(tags = listOf(excluded)),
                        recipe(id = "recipe-2").copy(tags = listOf(avoided)),
                    ),
                )

            val result = orchestrator(FakeRecipeAgent("agent-1", answer)).suggest(stored, emptyList(), options)

            assertEquals(listOf(recipeId("recipe-2")), result.unwrap().map { it.recipe.id })
        }

    @Test
    fun `coverage is computed against the pantry and never taken from the answer`() =
        runTest {
            val proposed = recipe(ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(2.0, unit))))
            val answer = agentAnswer("agent-1", listOf(proposed))
            val held = listOf(pantryItem("item-1", "ing-1", Quantity(2.0, unit)))

            val covered = orchestrator(FakeRecipeAgent("agent-1", answer)).suggest(profile(), held, options)
            val bare = orchestrator(FakeRecipeAgent("agent-1", answer)).suggest(profile(), emptyList(), options)

            assertEquals(1f, covered.unwrap().single().match.coverage)
            assertEquals(0f, bare.unwrap().single().match.coverage)
        }

    @Test
    fun `provenance is stamped by the orchestrator and not by the recipe it received`() =
        runTest {
            // The fixture claims the catalogue as its source; an agent saying so does not make it true.
            val answer = agentAnswer("agent-1", listOf(recipe()))

            val result = orchestrator(FakeRecipeAgent("agent-1", answer)).suggest(profile(), emptyList(), options)

            assertEquals(RecipeSource.Agent(agentId("agent-1"), "model-agent-1", now), result.unwrap().single().source)
        }

    @Test
    fun `no more suggestions leave than the caller asked for`() =
        runTest {
            val answer = agentAnswer("agent-1", listOf(recipe(id = "recipe-1"), recipe(id = "recipe-2")))

            val result =
                orchestrator(FakeRecipeAgent("agent-1", answer))
                    .suggest(profile(), emptyList(), options.copy(maxResults = 1))

            assertEquals(listOf(recipeId("recipe-1")), result.unwrap().map { it.recipe.id })
        }

    @Test
    fun `the options and the pantry the caller passed reach the agent`() =
        runTest {
            val agent = FakeRecipeAgent("agent-1", agentAnswer("agent-1", emptyList()))
            val held: List<PantryItem> = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, unit)))

            orchestrator(agent).suggest(profile(), held, options.copy(useOnlyPantry = true))

            val context = requireNotNull(agent.received)
            assertTrue(context.options.useOnlyPantry)
            assertEquals(listOf(ingredientId("ing-1")), context.pantry.map { it.ingredient })
        }

    private fun orchestrator(vararg agents: RecipeAgent): DefaultAgentOrchestrator =
        DefaultAgentOrchestrator(
            AgentRegistry { agents.toList() },
            DefaultAgentSelectionStrategy(),
            TimeProvider { now },
        )

    private fun AppResult<List<RecipeSuggestion>>.unwrap(): List<RecipeSuggestion> = (this as AppResult.Success).data
}
