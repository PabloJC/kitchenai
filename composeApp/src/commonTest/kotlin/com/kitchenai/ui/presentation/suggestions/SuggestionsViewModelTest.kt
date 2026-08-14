package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.agent.AgentOrchestrator
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.HouseholdContext
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.PantryPort
import com.kitchenai.shared.domain.port.UserProfilePort
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.recipe.SuggestRecipes
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val catalogue = FakeIngredientPort()
    private val agent = RecordingOrchestrator()
    private val cache = SuggestionCache()
    private val profile =
        UserProfile(
            userId = UserId.of("user-1").orFail(),
            displayName = null,
            languageTags = listOf("en"),
            household = HouseholdContext(servings = 2),
            constraints = emptyList(),
            preferences = emptyList(),
            avoidedIngredients = emptyList(),
            updatedAt = kotlin.time.Instant.fromEpochSeconds(0),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `asks for nothing until the user asks`() =
        runTest(dispatcher) {
            started()
            advanceUntilIdle()

            // The whole point of the screen: a tab tap must not bill for a model call.
            assertEquals(0, agent.calls)
        }

    @Test
    fun `generating moves through working and lands on the suggestions`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()

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

            val message = viewModel.state.value.error.orEmpty()
            assertTrue(message.contains("prove who it is"), message)
            assertFalse(message.contains("connection"), message)
        }

    @Test
    fun `a network failure says so`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Failure(AppError.Network())
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            assertEquals("No connection", viewModel.state.value.error)
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
    fun `a generated dish is reachable before its card is`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started()

            viewModel.generate()
            advanceUntilIdle()

            // It exists nowhere else: the detail screen has only this to open.
            val id = viewModel.state.value.suggestions.single().id
            assertEquals("Dish", cache[id]?.title)
        }

    private fun started(): SuggestionsViewModel =
        SuggestionsViewModel(
            suggestRecipes = SuggestRecipes(StubProfilePort(profile), StubPantryPort(), agent),
            cache = cache,
            observeIngredients = ObserveIngredients(catalogue),
            dispatchers = TestDispatcherProvider(dispatcher),
        ).also { it.start(UserId.of("user-1").orFail()) }
}

/**
 * The real use case over fake ports, so the wiring it does is exercised too. The orchestrator
 * is what counts calls, because the number of times a model is asked is the assertion this
 * screen exists to make.
 */
private class RecordingOrchestrator : AgentOrchestrator {
    var answer: AppResult<List<RecipeSuggestion>> = AppResult.Success(emptyList())
    var calls = 0
        private set
    var lastOptions: SuggestionOptions? = null
        private set

    override suspend fun suggest(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
    ): AppResult<List<RecipeSuggestion>> {
        calls++
        lastOptions = options
        return answer
    }
}

private class StubProfilePort(private val profile: UserProfile) : UserProfilePort {
    override fun observeProfile(userId: UserId): Flow<UserProfile> = flowOf(profile)

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun save(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)
}

private class StubPantryPort : PantryPort {
    override fun observePantry(userId: UserId): Flow<List<PantryItem>> = flowOf(emptyList())

    override fun pantryErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getPantry(userId: UserId): AppResult<List<PantryItem>> = AppResult.Success(emptyList())

    override suspend fun upsert(
        userId: UserId,
        item: PantryItem,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun remove(
        userId: UserId,
        id: PantryItemId,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun upsertAll(
        userId: UserId,
        items: List<PantryItem>,
    ): AppResult<Unit> = AppResult.Success(Unit)
}

private fun suggestion(
    id: String,
    source: RecipeSource = RecipeSource.Catalogue,
): RecipeSuggestion {
    val recipe =
        Recipe(
            id = RecipeId.of(id).orFail(),
            title = "Dish",
            summary = null,
            servings = 2,
            totalMinutes = 20,
            ingredients = listOf(RecipeIngredient(null, "salt", null, false)),
            steps = listOf("Step"),
            tags = emptyList(),
            source = source,
        )
    return RecipeSuggestion(
        recipe = recipe,
        match = PantryMatch(recipe.id, emptyList(), emptyList(), recipe.ingredients),
        source = source,
    )
}

private fun <T> AppResult<T>.orFail(): T = (this as AppResult.Success).data
