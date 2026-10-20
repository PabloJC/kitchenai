package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeRecipePort
import com.kitchenai.ui.presentation.common.FlakyPantryPort
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.defaultKitchenId
import com.kitchenai.ui.presentation.common.kitchen
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_timeout
import com.kitchenai.ui.resources.error_unauthorized_action
import com.kitchenai.ui.resources.error_unauthorized_suggestions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsViewModelTest : SuggestionsViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a launch shows what was stored immediately before the new generation returns`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(stored = listOf(dish("recipe-1")))
            agent.gate = CompletableDeferred()
            agent.answer = AppResult.Success(listOf(suggestion("recipe-2")))

            val viewModel = started(recipes = recipes)
            dispatcher.scheduler.runCurrent()

            // The stored set is up, and the new run is still in flight behind it.
            assertEquals(listOf("recipe-1"), viewModel.state.value.suggestions.map { it.id.value })
            assertTrue(viewModel.state.value.isGenerating)

            agent.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("recipe-2"), viewModel.state.value.suggestions.map { it.id.value })
        }

    @Test
    fun `a kitchen change cancels an in-flight generation instead of leaving it stuck`() =
        runTest(dispatcher) {
            agent.gate = CompletableDeferred()

            val viewModel = started()
            dispatcher.scheduler.runCurrent()

            // The first kitchen's generation is in flight, gated behind the model call.
            assertTrue(viewModel.state.value.isGenerating)
            assertEquals(1, agent.calls)

            kitchens.emit(kitchen(id = KitchenId.of("kitchen-2").orFail()))
            advanceUntilIdle()

            // A second call for the new kitchen, not a silent no-op behind a guard the first,
            // now-cancelled call left stuck at true.
            assertEquals(2, agent.calls)
            assertTrue(viewModel.state.value.isGenerating)

            agent.gate?.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isGenerating)
        }

    @Test
    fun `a stored suggestion's ingredient names update once the catalogue answers`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(stored = listOf(shortOfRice().recipe))
            agent.gate = CompletableDeferred()

            val viewModel = started(recipes = recipes)
            dispatcher.scheduler.runCurrent()

            // Shown immediately, before the catalogue listener (started alongside it) has
            // necessarily answered: the honest fallback is the raw id, not a blank card.
            assertEquals(listOf("rice"), viewModel.state.value.suggestions.single().missing)

            catalogue.emit(listOf(ingredient("rice", mapOf("en" to "Rice"))))
            dispatcher.scheduler.runCurrent()

            // The same card, re-resolved — not only a suggestion generated after this point.
            assertEquals(listOf("Rice"), viewModel.state.value.suggestions.single().missing)
        }

    @Test
    fun `a launch generates exactly once`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(emptyList())
            started()
            advanceUntilIdle()

            assertEquals(1, agent.calls)
        }

    @Test
    fun `entering and leaving the tab again within the same session bills nothing further`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(emptyList())
            val viewModel = started()
            advanceUntilIdle()

            // The screen is composed again, as it would be on a tab switch back.
            viewModel.start(UserId.of("user-1").orFail(), listOf("en"), "List")
            advanceUntilIdle()

            assertEquals(1, agent.calls)
        }

    @Test
    fun `generating moves through working and lands on the suggestions`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()
            // Lets the kitchen listener resolve (and the launch's own auto-generate run and
            // finish) before this test drives the button's own call to generate().
            advanceUntilIdle()

            viewModel.generate()
            assertTrue(viewModel.state.value.isGenerating)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isGenerating)
            assertTrue(viewModel.state.value.hasGenerated)
            assertEquals(listOf("recipe-1"), viewModel.state.value.suggestions.map { it.id.value })
        }

    @Test
    fun `a second tap while one is in flight is ignored rather than billed twice`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()

            viewModel.generate()
            viewModel.generate()
            viewModel.generate()
            advanceUntilIdle()

            assertEquals(1, agent.calls)
        }

    @Test
    fun `an empty answer reads as generated rather than as never asked`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(emptyList())
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.suggestions.isEmpty())
            // The difference between "nothing matched" and "you have not asked yet".
            assertTrue(viewModel.state.value.hasGenerated)
        }

    @Test
    fun `an unauthorized failure does not blame the connection`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Failure(AppError.Unauthorized())
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            // Its own sentence, not the connection one: the key is the assertion now, which
            // survives a reword and a translation where matching on prose did neither.
            assertEquals(UiText.of(Res.string.error_unauthorized_suggestions), viewModel.state.value.error)
        }

    @Test
    fun `a timeout does not blame the connection and says retrying is the fix`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Failure(AppError.Timeout())
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            // A cold start is the likeliest cause here, and the connection is not at fault.
            assertEquals(UiText.of(Res.string.error_timeout), viewModel.state.value.error)
        }

    @Test
    fun `a network failure says so`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Failure(AppError.Network())
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)
            assertFalse(viewModel.state.value.isGenerating)
        }

    @Test
    fun `a generated suggestion carries both halves of its provenance`() =
        runTest(dispatcher) {
            val by =
                RecipeSource.Agent(
                    agentId = AgentId.of("agent-1").orFail(),
                    modelId = "model-1",
                    generatedAt = kotlin.time.Instant.fromEpochSeconds(0),
                )
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1", by)))
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            // Agent and model both, not a boolean: support cannot chase a bad dish without them.
            assertEquals(ProvenanceUi("agent-1", "model-1"), viewModel.state.value.suggestions.single().provenance)
        }

    @Test
    fun `a catalogue dish is attributed to nobody`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            assertEquals(null, viewModel.state.value.suggestions.single().provenance)
        }

    @Test
    fun `a missing catalogue ingredient is named rather than identified`() =
        runTest(dispatcher) {
            catalogue.emit(listOf(ingredient("rice", mapOf("en" to "Rice"))))
            agent.answer = AppResult.Success(listOf(shortOfRice()))
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            // The card names it. Every other fixture here carries free text, which is its own
            // name and never asks the resolver — which is why this went unnoticed.
            assertEquals(listOf("Rice"), viewModel.state.value.suggestions.single().missing)
        }

    @Test
    fun `the options the user set reach the use case`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(emptyList())
            val viewModel = started()

            viewModel.setUseOnlyPantry(true)
            viewModel.setMaxMinutes(30)
            viewModel.setMaxResults(2)
            viewModel.generate()
            advanceUntilIdle()

            val sent = requireNotNull(agent.lastOptions)
            assertTrue(sent.useOnlyPantry)
            assertEquals(30, sent.maxMinutes)
            assertEquals(2, sent.maxResults)
        }

    /**
     * #131: the profile's own stored tags stay whatever they were on first launch; the language
     * an agent is asked to answer in has to come from the device, every time, or a user who
     * changes their device language keeps getting suggestions in the old one forever.
     */
    @Test
    fun `the device's current language reaches the agent rather than the profile's stored one`() =
        runTest(dispatcher) {
            val viewModel = started(languageTags = listOf("es"))

            viewModel.generate()
            advanceUntilIdle()

            assertEquals(listOf("en"), profile.languageTags)
            assertEquals(listOf("es"), agent.lastLanguageTags)
        }

    @Test
    fun `a failure clears when the next run succeeds`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Failure(AppError.Network())
            val viewModel = started()
            viewModel.generate()
            advanceUntilIdle()

            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            viewModel.generate()
            advanceUntilIdle()

            assertEquals(null, viewModel.state.value.error)
        }

    @Test
    fun `a generated dish is stored so it is reachable after a restart`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started(recipes = recipes)

            viewModel.generate()
            advanceUntilIdle()

            // It exists nowhere else: the detail screen, on a fresh launch, has only this to open.
            val id = viewModel.state.value.suggestions.single().id
            val stored = (recipes.getAll() as AppResult.Success).data
            assertEquals("Dish", stored.single { it.id == id }.title)
        }

    @Test
    fun `a saved recipe is exposed separately from the generated suggestions and matched against the pantry`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            recipes.saveRecipe(defaultKitchenId, dish("recipe-1"))
            agent.answer = AppResult.Success(listOf(suggestion("recipe-2")))

            val viewModel = started(recipes = recipes)
            advanceUntilIdle()

            // Two different facts, two different fields: what was just generated, and what was
            // kept on some earlier visit. Neither list is the other with items removed.
            assertEquals(listOf("recipe-2"), viewModel.state.value.suggestions.map { it.id.value })
            assertEquals(listOf("recipe-1"), viewModel.state.value.savedRecipes.map { it.id.value })
        }

    @Test
    fun `a saved recipe's match refreshes when the pantry changes and not only when the saved list does`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            recipes.saveRecipe(defaultKitchenId, shortOfRice().recipe)
            val pantry = FakePantryPort()
            agent.answer = AppResult.Success(emptyList())

            val viewModel = started(recipes = recipes, pantryPort = pantry)
            advanceUntilIdle()

            assertEquals(listOf("rice"), viewModel.state.value.savedRecipes.single().missing)

            pantry.upsert(defaultKitchenId, riceHolding())
            advanceUntilIdle()

            // matchRecipe reads the pantry once per call rather than as a listener; this is
            // start()'s own combine re-invoking it, not something matchRecipe did on its own.
            assertTrue(viewModel.state.value.savedRecipes.single().missing.isEmpty())
        }

    @Test
    fun `a generated card's coverage follows the pantry without another model call`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(flourHolding()))
            agent.answer = AppResult.Success(listOf(shortOfRice()))
            val viewModel = started(pantryPort = pantry)
            advanceUntilIdle()

            val before = viewModel.state.value.suggestions.single()
            assertEquals(listOf("rice"), before.missing)
            assertEquals(0, before.heldCount)

            pantry.upsert(defaultKitchenId, riceHolding())
            advanceUntilIdle()

            // The stamped match said short of rice; shopping moved it without regenerating.
            val after = viewModel.state.value.suggestions.single()
            assertTrue(after.missing.isEmpty())
            assertEquals(1, after.heldCount)
            assertEquals(1f, after.coverage)
            assertEquals(1, agent.calls)
        }

    @Test
    fun `a re-match that fails leaves the generated card as it was`() =
        runTest(dispatcher) {
            val pantry = FlakyPantryPort(FakePantryPort(listOf(flourHolding())))
            agent.answer = AppResult.Success(listOf(shortOfRice()))
            val viewModel = started(pantryPort = pantry)
            advanceUntilIdle()
            val before = viewModel.state.value

            pantry.failReads = true
            pantry.upsert(defaultKitchenId, riceHolding())
            advanceUntilIdle()

            // The stamped match stays and nobody is told: a failed read is not the user's doing.
            assertEquals(before, viewModel.state.value)
            assertEquals(null, viewModel.state.value.error)
        }

    @Test
    fun `a re-match that lands after a newer generation does not overwrite it`() =
        runTest(dispatcher) {
            val pantry = FlakyPantryPort(FakePantryPort(listOf(flourHolding())))
            agent.answer = AppResult.Success(listOf(shortOfRice()))
            val viewModel = started(pantryPort = pantry)
            advanceUntilIdle()
            assertEquals(listOf("recipe-1"), viewModel.state.value.suggestions.map { it.id.value })

            // The pantry changes and the re-match of the first list is held mid-read...
            val late = CompletableDeferred<Unit>()
            pantry.holdNextRead = late
            pantry.upsert(defaultKitchenId, riceHolding())
            dispatcher.scheduler.runCurrent()

            // ...while a newer generation replaces that list.
            agent.answer = AppResult.Success(listOf(suggestion("recipe-2")))
            viewModel.generate()
            dispatcher.scheduler.runCurrent()
            assertEquals(listOf("recipe-2"), viewModel.state.value.suggestions.map { it.id.value })

            late.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("recipe-2"), viewModel.state.value.suggestions.map { it.id.value })
        }

    @Test
    fun `a pantry change during generation is reflected when the suggestions land`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(flourHolding()))
            agent.gate = CompletableDeferred()
            agent.answer = AppResult.Success(listOf(shortOfRice()))
            val viewModel = started(pantryPort = pantry)
            dispatcher.scheduler.runCurrent()
            assertTrue(viewModel.state.value.isGenerating)

            // The stamped match was computed before this, and nothing on screen to re-match yet.
            pantry.upsert(defaultKitchenId, riceHolding())
            dispatcher.scheduler.runCurrent()
            agent.gate?.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.suggestions.single().missing.isEmpty())
        }

    @Test
    fun `filters read as unchanged until a run has landed`() =
        runTest(dispatcher) {
            agent.gate = CompletableDeferred()
            val viewModel = started()
            dispatcher.scheduler.runCurrent()

            viewModel.setUseOnlyPantry(true)

            // Nothing on screen was generated with other options, so there is nothing to update.
            assertFalse(viewModel.state.value.optionsChanged)
        }

    @Test
    fun `changing a filter after a run offers an update and does not call the model`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.optionsChanged)

            viewModel.setUseOnlyPantry(true)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.optionsChanged)
            assertEquals(1, agent.calls)
        }

    @Test
    fun `updating with the new filters regenerates with them and clears the offer`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()
            advanceUntilIdle()
            viewModel.setMaxMinutes(30)
            assertTrue(viewModel.state.value.optionsChanged)

            viewModel.generate()
            advanceUntilIdle()

            assertEquals(30, agent.lastOptions?.maxMinutes)
            assertFalse(viewModel.state.value.optionsChanged)
        }

    @Test
    fun `a filter edited while a run is in flight still reads as changed once it lands`() =
        runTest(dispatcher) {
            agent.gate = CompletableDeferred()
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()
            dispatcher.scheduler.runCurrent()

            viewModel.setUseOnlyPantry(true)
            agent.gate?.complete(Unit)
            advanceUntilIdle()

            // The run was sent without the filter, so what is on screen does not honour it.
            assertTrue(viewModel.state.value.optionsChanged)
        }

    @Test
    fun `cancelling a run keeps the previous results and shows no later answer`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(stored = listOf(dish("recipe-1")))
            agent.gate = CompletableDeferred()
            agent.answer = AppResult.Success(listOf(suggestion("recipe-2")))
            val viewModel = started(recipes = recipes)
            dispatcher.scheduler.runCurrent()
            assertTrue(viewModel.state.value.isGenerating)

            viewModel.cancelGeneration()
            agent.gate?.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isGenerating)
            // The late answer never lands: the stored set is what is on screen.
            assertEquals(listOf("recipe-1"), viewModel.state.value.suggestions.map { it.id.value })
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `a failure that arrives after a cancel is not shown`() =
        runTest(dispatcher) {
            agent.gate = CompletableDeferred()
            agent.answer = AppResult.Failure(AppError.Network())
            val viewModel = started()
            dispatcher.scheduler.runCurrent()

            viewModel.cancelGeneration()
            agent.gate?.complete(Unit)
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isGenerating)
        }

    @Test
    fun `a run can be started again after a cancel`() =
        runTest(dispatcher) {
            agent.gate = CompletableDeferred()
            val viewModel = started()
            dispatcher.scheduler.runCurrent()
            viewModel.cancelGeneration()
            advanceUntilIdle()

            agent.gate = null
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            viewModel.generate()
            advanceUntilIdle()

            assertEquals(2, agent.calls)
            assertEquals(listOf("recipe-1"), viewModel.state.value.suggestions.map { it.id.value })
        }

    @Test
    fun `cancelling with nothing in flight does nothing`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()
            advanceUntilIdle()
            val before = viewModel.state.value

            viewModel.cancelGeneration()

            assertEquals(before, viewModel.state.value)
        }

    @Test
    fun `a failed run offers a retry that runs again`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Failure(AppError.Network())
            val viewModel = started()
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)

            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            viewModel.generate()
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertEquals(2, agent.calls)
        }

    @Test
    fun `a card lists three missing ingredients and counts the rest`() {
        val ui = suggestionUi(missing = listOf("a", "b", "c", "d", "e"))

        assertEquals(listOf("a", "b", "c"), ui.shownMissing)
        assertEquals(2, ui.hiddenMissingCount)
    }

    @Test
    fun `a card with three or fewer missing ingredients hides none`() {
        val ui = suggestionUi(missing = listOf("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), ui.shownMissing)
        assertEquals(0, ui.hiddenMissingCount)
    }

    @Test
    fun `adding a card's missing ingredients writes them to the list and says how many`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(shortOfRice()))
            val viewModel = started()
            advanceUntilIdle()
            val seen = mutableListOf<SuggestionsEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.addMissingToList(RecipeId.of("recipe-1").orFail())
            advanceUntilIdle()
            collector.cancel()

            assertEquals(SuggestionsEvent.AddedToList(added = 1, skipped = 0), seen.single())
            assertEquals(1, items.upserts.single().size)
            assertNull(viewModel.state.value.addingMissingFor)
        }

    @Test
    fun `a saved recipe's missing ingredients can be added too`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            recipes.saveRecipe(defaultKitchenId, shortOfRice().recipe)
            agent.answer = AppResult.Success(emptyList())
            val viewModel = started(recipes = recipes)
            advanceUntilIdle()

            viewModel.addMissingToList(RecipeId.of("recipe-1").orFail())
            advanceUntilIdle()

            assertEquals(1, items.upserts.single().size)
        }

    @Test
    fun `a write that fails says so instead of claiming it added`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(shortOfRice()))
            items.upsertResult = AppResult.Failure(AppError.Unauthorized())
            val viewModel = started()
            advanceUntilIdle()
            val seen = mutableListOf<SuggestionsEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.addMissingToList(RecipeId.of("recipe-1").orFail())
            advanceUntilIdle()
            collector.cancel()

            val expected = UiText.of(Res.string.error_unauthorized_action)
            assertEquals(SuggestionsEvent.Failed(expected), seen.single())
            assertNull(viewModel.state.value.addingMissingFor)
        }

    @Test
    fun `adding for a card that is not on screen writes nothing`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(shortOfRice()))
            val viewModel = started()
            advanceUntilIdle()

            viewModel.addMissingToList(RecipeId.of("somewhere-else").orFail())
            advanceUntilIdle()

            assertTrue(items.upserts.isEmpty())
        }

    @Test
    fun `the added-to-list sentence agrees each count with its noun`() {
        val sentence = SuggestionsEvent.AddedToList(added = 2, skipped = 1).sentence()

        val joined = sentence as UiText.Joined
        assertEquals(listOf(2, 1), joined.parts.map { (it as UiText.Plural).quantity })
    }
}

private fun ingredient(
    id: String,
    labels: Map<String, String>,
): Ingredient = Ingredient(IngredientId.of(id).orFail(), labels, null, emptyList())

private fun suggestionUi(missing: List<String>): SuggestionUi =
    SuggestionUi(
        id = RecipeId.of("recipe-1").orFail(),
        title = "Dish",
        summary = null,
        totalMinutes = null,
        coverage = 0f,
        heldCount = 0,
        totalCount = missing.size,
        missing = missing,
        unverifiable = emptyList(),
        provenance = null,
    )
