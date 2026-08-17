package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.agent.AgentOrchestrator
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.agent.profile
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.termRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestRecipesUseCaseTest {
    private val stored = profile()
    private val held = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, termRef("term-1"))))

    @Test
    fun `hands the stored profile and pantry to the orchestrator`() =
        runTest {
            val orchestrator = RecordingOrchestrator()
            val useCase =
                SuggestRecipesUseCase(FakeProfilePort(flowOf(stored)), FakePantryRepositoryContract(held), orchestrator)

            val result = useCase(user, listOf("en"), SuggestionOptions(useOnlyPantry = true))

            assertTrue(result is AppResult.Success)
            assertEquals(stored, orchestrator.profile)
            assertEquals(held, orchestrator.pantry)
            assertEquals(true, orchestrator.options?.useOnlyPantry)
        }

    /** #131: the profile's own stored tags are a fact about the person, not about this request. */
    @Test
    fun `the caller's language reaches the orchestrator rather than the profile's stored one`() =
        runTest {
            val orchestrator = RecordingOrchestrator()
            val useCase =
                SuggestRecipesUseCase(FakeProfilePort(flowOf(stored)), FakePantryRepositoryContract(held), orchestrator)

            useCase(user, listOf("es"))

            assertEquals(listOf("xx"), stored.languageTags)
            assertEquals(listOf("es"), orchestrator.languageTags)
        }

    @Test
    fun `a failing pantry read is reported`() =
        runTest {
            val pantry = FakePantryRepositoryContract(readError = AppError.Network())
            val useCase = SuggestRecipesUseCase(FakeProfilePort(flowOf(stored)), pantry, RecordingOrchestrator())

            assertTrue(useCase(user, listOf("en")) is AppResult.Failure)
        }

    @Test
    fun `a profile listener that has already failed is reported and never hangs`() =
        runTest {
            val useCase =
                SuggestRecipesUseCase(
                    FakeProfilePort(emptyFlow()),
                    FakePantryRepositoryContract(held),
                    RecordingOrchestrator(),
                )

            val result = useCase(user, listOf("en"))

            assertEquals(AppError.NotFound("profile"), (result as AppResult.Failure).error)
        }
}

private class RecordingOrchestrator : AgentOrchestrator {
    var profile: UserProfile? = null
        private set
    var pantry: List<PantryItem>? = null
        private set
    var options: SuggestionOptions? = null
        private set
    var languageTags: List<String>? = null
        private set

    override suspend fun suggest(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        languageTags: List<String>,
    ): AppResult<List<RecipeSuggestion>> {
        this.profile = profile
        this.pantry = pantry
        this.options = options
        this.languageTags = languageTags
        return AppResult.Success(emptyList())
    }
}

private class FakeProfilePort(
    private val stream: Flow<UserProfile>,
) : UserProfileRepositoryContract {
    override fun observeProfile(userId: UserId): Flow<UserProfile> = stream

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun save(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)
}
